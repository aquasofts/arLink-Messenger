package websocket

import (
	"context"
	"encoding/json"
	"sync"
	"testing"
	"time"

	"github.com/nearlink/nearlink-server/internal/presence"
	"github.com/nearlink/nearlink-server/internal/ratelimit"
	"github.com/nearlink/nearlink-server/internal/storage"
	"go.uber.org/zap"
)

// 内存 store mock：仅用于单测，实现 storage.Store 必需方法。
type memStore struct {
	mu  sync.Mutex
	off []storage.Envelope
}

func (m *memStore) UpsertDevice(ctx context.Context, _ string, _ []byte, _ time.Time) error {
	return nil
}
func (m *memStore) GetDevicePub(ctx context.Context, _ string) ([]byte, error)      { return nil, nil }
func (m *memStore) UpdateLastSeen(ctx context.Context, _ string, _ time.Time) error { return nil }
func (m *memStore) EnqueueOffline(ctx context.Context, e storage.Envelope) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.off = append(m.off, e)
	return nil
}
func (m *memStore) DrainOffline(ctx context.Context, to string, since time.Time, lim int) ([]storage.Envelope, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := []storage.Envelope{}
	for _, e := range m.off {
		if e.ToDeviceID == to && e.CreatedAt.After(since) {
			out = append(out, e)
		}
		if len(out) >= lim {
			break
		}
	}
	return out, nil
}
func (m *memStore) DeleteOffline(ctx context.Context, ids []string) error   { return nil }
func (m *memStore) GCOffline(ctx context.Context, _ time.Time) (int, error) { return 0, nil }
func (m *memStore) PutFileChunkMeta(ctx context.Context, _ string, _, _ int, _ string) error {
	return nil
}
func (m *memStore) HasFileChunk(ctx context.Context, _ string, _ int) (bool, error) {
	return false, nil
}
func (m *memStore) Close() error { return nil }

// fakeClient 直接挂在 hub 里，绕过真实 ws conn。
func fakeClient(deviceID string, sink chan WireFrame) *Client {
	c := &Client{deviceID: deviceID, writeMu: &sync.Mutex{}, writeWait: time.Second}
	c.send = func(f WireFrame) error {
		select {
		case sink <- f:
		default:
		}
		return nil
	}
	return c
}

func newHub() *Hub {
	return NewHub(HubConfig{
		Logger:   zap.NewNop(),
		Presence: presence.New(),
		Store:    &memStore{},
		Rate:     ratelimit.New(100, 100),
		MaxBytes: 64 * 1024,
	})
}

func TestRoute_OnlinePeer_RelaysAndAcks(t *testing.T) {
	h := newHub()
	senderSink := make(chan WireFrame, 8)
	targetSink := make(chan WireFrame, 8)
	sender := fakeClient("alice", senderSink)
	target := fakeClient("bob", targetSink)
	h.Register(sender)
	h.Register(target)

	err := h.Route(context.Background(), sender, EncryptedMessage{
		ClientMsgID: "m1", ConvID: "bob", ToDeviceID: "bob",
		Kind: "encrypted", Alg: "x", NonceB64: "n", EphemeralPubB64: "e",
		CiphertextB64: "c", Size: 10,
	})
	if err != nil {
		t.Fatal(err)
	}
	// target 应收到 msg_relay
	select {
	case f := <-targetSink:
		if f.Type != TypeMsgRelay {
			t.Fatalf("expected msg_relay, got %s", f.Type)
		}
	case <-time.After(time.Second):
		t.Fatal("target did not receive relay")
	}
	// sender 应收到 ack(relayed) + delivered
	relayedSeen, deliveredSeen := false, false
	for i := 0; i < 2; i++ {
		select {
		case f := <-senderSink:
			switch f.Type {
			case TypeMsgAck:
				relayedSeen = true
			case TypeMsgDelivered:
				var delivered MsgDelivered
				if err := json.Unmarshal(f.Payload, &delivered); err != nil {
					t.Fatal(err)
				}
				if delivered.ClientMsgID != "m1" {
					t.Fatalf("expected delivered client_msg_id m1, got %q", delivered.ClientMsgID)
				}
				deliveredSeen = true
			}
		case <-time.After(time.Second):
			t.Fatal("sender ack timeout")
		}
	}
	if !relayedSeen || !deliveredSeen {
		t.Fatalf("expected ack + delivered, got ack=%v delivered=%v", relayedSeen, deliveredSeen)
	}
}

func TestRoute_OfflinePeer_QueuesAndAcksQueued(t *testing.T) {
	h := newHub()
	senderSink := make(chan WireFrame, 8)
	sender := fakeClient("alice", senderSink)
	h.Register(sender)
	// 注意：未注册 bob

	err := h.Route(context.Background(), sender, EncryptedMessage{
		ClientMsgID: "m2", ConvID: "bob", ToDeviceID: "bob",
		Kind: "encrypted", Alg: "x", NonceB64: "n", EphemeralPubB64: "e",
		CiphertextB64: "c", Size: 10,
	})
	if err != nil {
		t.Fatal(err)
	}
	select {
	case f := <-senderSink:
		if f.Type != TypeMsgAck {
			t.Fatalf("expected msg_ack, got %s", f.Type)
		}
	case <-time.After(time.Second):
		t.Fatal("ack timeout")
	}
}

func TestRoute_RateLimited(t *testing.T) {
	h := NewHub(HubConfig{
		Logger:   zap.NewNop(),
		Presence: presence.New(),
		Store:    &memStore{},
		Rate:     ratelimit.New(1, 1),
		MaxBytes: 64 * 1024,
	})
	sink := make(chan WireFrame, 8)
	sender := fakeClient("alice", sink)
	h.Register(sender)
	h.Register(fakeClient("bob", make(chan WireFrame, 1)))

	// 第一条：通过
	if err := h.Route(context.Background(), sender, EncryptedMessage{
		ClientMsgID: "m1", ToDeviceID: "bob", ConvID: "bob",
		Alg: "x", NonceB64: "n", EphemeralPubB64: "e", CiphertextB64: "c", Size: 5,
	}); err != nil {
		t.Fatal(err)
	}
	// 排空 sink
	for len(sink) > 0 {
		<-sink
	}
	// 第二条：限流 → ack(rejected)
	if err := h.Route(context.Background(), sender, EncryptedMessage{
		ClientMsgID: "m2", ToDeviceID: "bob", ConvID: "bob",
		Alg: "x", NonceB64: "n", EphemeralPubB64: "e", CiphertextB64: "c", Size: 5,
	}); err != nil {
		t.Fatal(err)
	}
	select {
	case f := <-sink:
		if f.Type != TypeMsgAck {
			t.Fatalf("expected ack, got %s", f.Type)
		}
	case <-time.After(time.Second):
		t.Fatal("rate-limited ack timeout")
	}
}
