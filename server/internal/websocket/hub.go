package websocket

import (
	"context"
	"encoding/json"
	"sync"
	"time"

	gws "github.com/gorilla/websocket"
	"github.com/nearlink/nearlink-server/internal/presence"
	"github.com/nearlink/nearlink-server/internal/ratelimit"
	"github.com/nearlink/nearlink-server/internal/storage"
	"go.uber.org/zap"
)

// Hub 维护所有在线连接，按 device_id 索引。
type Hub struct {
	mu       sync.RWMutex
	clients  map[string]*Client
	log      *zap.Logger
	presence *presence.Tracker
	store    storage.Store
	rate     *ratelimit.Limiter

	maxBytes  int
	pingEvery time.Duration
	pongWait  time.Duration
	writeWait time.Duration
}

type HubConfig struct {
	Logger    *zap.Logger
	Presence  *presence.Tracker
	Store     storage.Store
	Rate      *ratelimit.Limiter
	MaxBytes  int
	PingEvery time.Duration
	PongWait  time.Duration
	WriteWait time.Duration
}

func NewHub(c HubConfig) *Hub {
	if c.MaxBytes <= 0 {
		c.MaxBytes = 64 * 1024
	}
	if c.PingEvery <= 0 {
		c.PingEvery = 30 * time.Second
	}
	if c.PongWait <= 0 {
		c.PongWait = 90 * time.Second
	}
	if c.WriteWait <= 0 {
		c.WriteWait = 10 * time.Second
	}
	return &Hub{
		clients:   map[string]*Client{},
		log:       c.Logger,
		presence:  c.Presence,
		store:     c.Store,
		rate:      c.Rate,
		maxBytes:  c.MaxBytes,
		pingEvery: c.PingEvery,
		pongWait:  c.PongWait,
		writeWait: c.WriteWait,
	}
}

func (h *Hub) Register(c *Client) {
	h.mu.Lock()
	// 同一 device 同时只保留最新连接；老连接关掉。
	if old, ok := h.clients[c.deviceID]; ok && old != c {
		old.close(gws.CloseGoingAway, "replaced")
	}
	h.clients[c.deviceID] = c
	h.mu.Unlock()
	h.presence.Connect(c.deviceID)
}

func (h *Hub) Unregister(c *Client) {
	h.mu.Lock()
	cur, ok := h.clients[c.deviceID]
	if ok && cur == c {
		delete(h.clients, c.deviceID)
	}
	h.mu.Unlock()
	if ok && cur == c {
		h.presence.Unsubscribe(c.deviceID)
		h.presence.Disconnect(c.deviceID)
		_ = h.store.UpdateLastSeen(context.Background(), c.deviceID, time.Now())
	}
}

func (h *Hub) ClientOf(deviceID string) (*Client, bool) {
	h.mu.RLock()
	c, ok := h.clients[deviceID]
	h.mu.RUnlock()
	return c, ok
}

// Route 把 msg_send 路由到目标 client，或入离线队列。
func (h *Hub) Route(ctx context.Context, sender *Client, payload EncryptedMessage) error {
	if !h.rate.Allow(sender.deviceID) {
		return sender.sendError(MsgAck{ClientMsgID: payload.ClientMsgID, Status: "rejected", Reason: "rate_limited"})
	}
	if payload.Size > h.maxBytes {
		return sender.sendError(MsgAck{ClientMsgID: payload.ClientMsgID, Status: "rejected", Reason: "too_large"})
	}

	target, online := h.ClientOf(payload.ToDeviceID)
	if online {
		// 直推：用 msg_relay 帧，from=sender.deviceID
		relay := NewFrame(TypeMsgRelay, sender.deviceID, target.deviceID, payload)
		if err := target.send(relay); err == nil {
			_ = sender.send(NewFrame(TypeMsgAck, "", sender.deviceID, MsgAck{
				ClientMsgID: payload.ClientMsgID, Status: "relayed",
			}))
			_ = sender.send(NewFrame(TypeMsgDelivered, "", sender.deviceID, MsgDelivered{
				ClientMsgID: payload.ClientMsgID, ServerMsgID: payload.ClientMsgID, ToDeviceID: target.deviceID,
			}))
			return nil
		}
		// 直推失败：降级到离线
	}
	// 离线入队
	env := storage.Envelope{
		ClientMsgID:   payload.ClientMsgID,
		FromDeviceID:  sender.deviceID,
		ToDeviceID:    payload.ToDeviceID,
		ConvID:        payload.ConvID,
		Alg:           payload.Alg,
		NonceB64:      payload.NonceB64,
		EphPubB64:     payload.EphemeralPubB64,
		CiphertextB64: payload.CiphertextB64,
		RefMsgID:      payload.RefMsgID,
		Size:          payload.Size,
		CreatedAt:     time.Now(),
	}
	if err := h.store.EnqueueOffline(ctx, env); err != nil {
		return sender.sendError(MsgAck{ClientMsgID: payload.ClientMsgID, Status: "rejected", Reason: "store_failed"})
	}
	return sender.send(NewFrame(TypeMsgAck, "", sender.deviceID, MsgAck{
		ClientMsgID: payload.ClientMsgID, Status: "queued",
	}))
}

// FlushOffline 在客户端 pull_offline 时调用。
func (h *Hub) FlushOffline(ctx context.Context, c *Client, sinceTs int64) error {
	since := time.UnixMilli(sinceTs)
	const batch = 200
	for {
		envs, err := h.store.DrainOffline(ctx, c.deviceID, since, batch)
		if err != nil {
			return err
		}
		if len(envs) == 0 {
			return c.send(NewFrame(TypePullOfflineChunk, "", c.deviceID, PullOfflineChunk{HasMore: false}))
		}
		frames := make([]WireFrame, 0, len(envs))
		ids := make([]string, 0, len(envs))
		for _, e := range envs {
			refMsg := ""
			if e.RefMsgID != nil {
				refMsg = *e.RefMsgID
			}
			payload := EncryptedMessage{
				ClientMsgID:     e.ClientMsgID,
				ConvID:          e.ConvID,
				ToDeviceID:      e.ToDeviceID,
				Kind:            "encrypted",
				Alg:             e.Alg,
				NonceB64:        e.NonceB64,
				EphemeralPubB64: e.EphPubB64,
				CiphertextB64:   e.CiphertextB64,
				Size:            e.Size,
			}
			if refMsg != "" {
				payload.RefMsgID = &refMsg
			}
			b, _ := json.Marshal(payload)
			frames = append(frames, WireFrame{
				V:       1,
				Type:    TypeMsgRelay,
				ID:      e.ServerMsgID,
				Ts:      e.CreatedAt.UnixMilli(),
				From:    e.FromDeviceID,
				To:      e.ToDeviceID,
				Payload: b,
			})
			ids = append(ids, e.ServerMsgID)
			since = e.CreatedAt
		}
		hasMore := len(envs) == batch
		if err := c.send(NewFrame(TypePullOfflineChunk, "", c.deviceID, PullOfflineChunk{
			Messages: frames, HasMore: hasMore,
		})); err != nil {
			return err
		}
		if err := h.store.DeleteOffline(ctx, ids); err != nil {
			h.log.Warn("delete offline failed", zap.Error(err))
		}
		if !hasMore {
			return nil
		}
	}
}
