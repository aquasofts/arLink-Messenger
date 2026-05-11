package storage

import (
	"context"
	"fmt"
	"testing"
	"time"
)

func newSQLite(t *testing.T) Store {
	t.Helper()
	s, err := OpenSQLite("file::memory:?cache=shared&_pragma=journal_mode(WAL)")
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	t.Cleanup(func() { _ = s.Close() })
	return s
}

func TestEnqueueAndDrain(t *testing.T) {
	ctx := context.Background()
	s := newSQLite(t)

	if err := s.UpsertDevice(ctx, "to1", []byte("pub"), time.Now()); err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	for i := 0; i < 3; i++ {
		err := s.EnqueueOffline(ctx, Envelope{
			ClientMsgID:   fmt.Sprintf("m%d", i),
			FromDeviceID:  "from1",
			ToDeviceID:    "to1",
			ConvID:        "from1",
			Alg:           "xchacha20poly1305",
			NonceB64:      "n", EphPubB64: "e", CiphertextB64: "c",
			Size: 100, CreatedAt: now.Add(time.Duration(i) * time.Millisecond),
		})
		if err != nil {
			t.Fatal(err)
		}
	}
	got, err := s.DrainOffline(ctx, "to1", time.Time{}, 100)
	if err != nil {
		t.Fatal(err)
	}
	if len(got) != 3 {
		t.Fatalf("want 3 got %d", len(got))
	}
}

func TestEnqueueDedup(t *testing.T) {
	ctx := context.Background()
	s := newSQLite(t)
	envelope := Envelope{
		ClientMsgID:   "dup",
		FromDeviceID:  "from",
		ToDeviceID:    "to",
		ConvID:        "from",
		Alg:           "x",
		NonceB64:      "n", EphPubB64: "e", CiphertextB64: "c",
		Size: 1, CreatedAt: time.Now(),
	}
	if err := s.EnqueueOffline(ctx, envelope); err != nil {
		t.Fatal(err)
	}
	// 第二次同 (to, client_msg_id) 应被 IGNORE
	if err := s.EnqueueOffline(ctx, envelope); err != nil {
		t.Fatal(err)
	}
	got, _ := s.DrainOffline(ctx, "to", time.Time{}, 100)
	if len(got) != 1 {
		t.Fatalf("expected dedup → 1, got %d", len(got))
	}
}

func TestGCOffline(t *testing.T) {
	ctx := context.Background()
	s := newSQLite(t)
	old := time.Now().Add(-48 * time.Hour)
	if err := s.EnqueueOffline(ctx, Envelope{
		ClientMsgID: "old", FromDeviceID: "f", ToDeviceID: "t", ConvID: "f",
		Alg: "x", NonceB64: "n", EphPubB64: "e", CiphertextB64: "c", Size: 1, CreatedAt: old,
	}); err != nil {
		t.Fatal(err)
	}
	n, err := s.GCOffline(ctx, time.Now().Add(-24*time.Hour))
	if err != nil {
		t.Fatal(err)
	}
	if n != 1 {
		t.Fatalf("expected gc 1, got %d", n)
	}
}
