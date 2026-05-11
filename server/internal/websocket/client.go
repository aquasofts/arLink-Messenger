package websocket

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"sync"
	"time"

	gws "github.com/gorilla/websocket"
	"github.com/google/uuid"
	"github.com/nearlink/nearlink-server/internal/auth"
	"go.uber.org/zap"
)

// Upgrader 处理 /v1/ws，把 HTTP 升级成 WS，并完成签名校验。
type Upgrader struct {
	Hub        *Hub
	Auth       *auth.Challenger
	Log        *zap.Logger
	ClockSkew  time.Duration
	MaxBytes   int

	wsUp gws.Upgrader
}

func (u *Upgrader) ensure() {
	if u.wsUp.ReadBufferSize == 0 {
		u.wsUp = gws.Upgrader{
			ReadBufferSize:  4096,
			WriteBufferSize: 4096,
			CheckOrigin:     func(r *http.Request) bool { return true }, // 由反向代理控制来源
		}
	}
}

func (u *Upgrader) Handle(w http.ResponseWriter, r *http.Request) {
	u.ensure()
	authHeader := r.Header.Get("X-NL-Auth")
	if authHeader == "" {
		http.Error(w, "missing X-NL-Auth", http.StatusUnauthorized)
		return
	}
	deviceID, pub, err := u.Auth.Verify(authHeader, u.ClockSkew)
	if err != nil {
		u.Log.Warn("ws auth failed", zap.String("err", err.Error()))
		http.Error(w, "auth: "+err.Error(), http.StatusUnauthorized)
		return
	}
	conn, err := u.wsUp.Upgrade(w, r, nil)
	if err != nil {
		u.Log.Warn("ws upgrade failed", zap.Error(err))
		return
	}
	if u.MaxBytes > 0 {
		conn.SetReadLimit(int64(u.MaxBytes) + 4096)
	}
	c := &Client{
		hub:      u.Hub,
		conn:     conn,
		deviceID: deviceID,
		pubKey:   pub,
		send:     u.write,
		writeMu:  &sync.Mutex{},
		writeWait: u.Hub.writeWait,
	}
	c.send = c.writeImpl
	_ = u.Hub.store.UpsertDevice(r.Context(), deviceID, pub, time.Now())
	u.Hub.Register(c)
	defer u.Hub.Unregister(c)

	// server_hello
	if err := c.send(NewFrame(TypeServerHello, "", deviceID, ServerHello{
		ServerTime: time.Now().UnixMilli(),
		SessionID:  uuid.NewString(),
	})); err != nil {
		return
	}
	c.readLoop(r.Context())
}

// 占位：避免 unused
func (u *Upgrader) write(_ WireFrame) error { return nil }

// Client 表示一个活跃 ws 连接。
type Client struct {
	hub *Hub

	conn      *gws.Conn
	deviceID  string
	pubKey    []byte
	writeMu   *sync.Mutex
	writeWait time.Duration

	send func(WireFrame) error
}

func (c *Client) writeImpl(f WireFrame) error {
	b, err := json.Marshal(f)
	if err != nil {
		return err
	}
	c.writeMu.Lock()
	defer c.writeMu.Unlock()
	_ = c.conn.SetWriteDeadline(time.Now().Add(c.writeWait))
	return c.conn.WriteMessage(gws.TextMessage, b)
}

func (c *Client) sendError(payload any) error {
	return c.send(NewFrame(TypeMsgAck, "", c.deviceID, payload))
}

func (c *Client) close(code int, reason string) {
	_ = c.conn.WriteControl(gws.CloseMessage,
		gws.FormatCloseMessage(code, reason),
		time.Now().Add(time.Second))
	_ = c.conn.Close()
}

func (c *Client) readLoop(ctx context.Context) {
	_ = c.conn.SetReadDeadline(time.Now().Add(c.hub.pongWait))
	c.conn.SetPongHandler(func(string) error {
		return c.conn.SetReadDeadline(time.Now().Add(c.hub.pongWait))
	})

	// 启动 ping 协程
	pingDone := make(chan struct{})
	go func() {
		t := time.NewTicker(c.hub.pingEvery)
		defer t.Stop()
		for {
			select {
			case <-pingDone:
				return
			case <-t.C:
				c.writeMu.Lock()
				_ = c.conn.SetWriteDeadline(time.Now().Add(c.writeWait))
				err := c.conn.WriteMessage(gws.PingMessage, nil)
				c.writeMu.Unlock()
				if err != nil {
					return
				}
			}
		}
	}()
	defer close(pingDone)

	for {
		_, data, err := c.conn.ReadMessage()
		if err != nil {
			if !errors.Is(err, gws.ErrCloseSent) {
				c.hub.log.Debug("ws read end", zap.String("dev", c.deviceID[:8]), zap.Error(err))
			}
			return
		}
		var frame WireFrame
		if err := json.Unmarshal(data, &frame); err != nil {
			_ = c.send(NewFrame(TypeError, "", c.deviceID, WireError{
				Code: "400_bad_frame", Message: err.Error(),
			}))
			continue
		}
		c.dispatch(ctx, frame)
	}
}

func (c *Client) dispatch(ctx context.Context, frame WireFrame) {
	switch frame.Type {
	case TypePing:
		_ = c.send(NewFrame(TypePong, "", c.deviceID, json.RawMessage(frame.Payload)))
	case TypeMsgSend:
		// 严禁伪造 from
		if frame.From != "" && frame.From != c.deviceID {
			_ = c.send(NewFrame(TypeError, "", c.deviceID, WireError{
				Code: "403_forbidden", Message: "from_mismatch",
			}))
			return
		}
		var p EncryptedMessage
		if err := json.Unmarshal(frame.Payload, &p); err != nil {
			_ = c.send(NewFrame(TypeError, "", c.deviceID, WireError{Code: "400_bad_frame", Message: err.Error()}))
			return
		}
		if err := c.hub.Route(ctx, c, p); err != nil {
			c.hub.log.Warn("route failed", zap.Error(err))
		}
	case TypePresenceSub:
		var p PresenceSub
		if err := json.Unmarshal(frame.Payload, &p); err == nil {
			ch := c.hub.presence.Subscribe(c.deviceID, p.DeviceIDs)
			go func() {
				for ev := range ch {
					state := "offline"
					if ev.Online {
						state = "server_online"
					}
					lastSeen := ev.LastSeen
					_ = c.send(NewFrame(TypePresenceUpdate, "", c.deviceID, PresenceUpdate{
						DeviceID: ev.DeviceID, State: state, LastSeen: &lastSeen,
					}))
				}
			}()
		}
	case TypePullOffline:
		var p PullOffline
		if err := json.Unmarshal(frame.Payload, &p); err == nil {
			if err := c.hub.FlushOffline(ctx, c, p.SinceTs); err != nil {
				c.hub.log.Warn("flush offline failed", zap.Error(err))
			}
		}
	case TypeMsgRead, TypeMsgTyping, TypeMsgRevoke, TypeMsgEdit, TypeMsgReaction:
		// 控制类消息：直接走 msg_send 路径以保持加密语义（客户端实际会用 msg_send 包装）。
		// 留作扩展点。
	default:
		_ = c.send(NewFrame(TypeError, "", c.deviceID, WireError{
			Code: "400_bad_frame", Message: "unknown type: " + frame.Type,
		}))
	}
}
