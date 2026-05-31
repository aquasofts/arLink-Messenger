package websocket

import (
	"encoding/json"
	"time"

	"github.com/google/uuid"
)

// WireFrame 与 Android 端协议帧对齐（见 docs/protocol.md §3）。
type WireFrame struct {
	V       int             `json:"v"`
	Type    string          `json:"type"`
	ID      string          `json:"id"`
	Ts      int64           `json:"ts"`
	From    string          `json:"from,omitempty"`
	To      string          `json:"to,omitempty"`
	Payload json.RawMessage `json:"payload,omitempty"`
}

const (
	TypeServerHello      = "server_hello"
	TypePing             = "ping"
	TypePong             = "pong"
	TypePresenceSub      = "presence_sub"
	TypePresenceUpdate   = "presence_update"
	TypeMsgSend          = "msg_send"
	TypeMsgRelay         = "msg_relay"
	TypeMsgAck           = "msg_ack"
	TypeMsgDelivered     = "msg_delivered"
	TypeMsgRead          = "msg_read"
	TypeMsgTyping        = "msg_typing"
	TypeMsgRevoke        = "msg_revoke"
	TypeMsgEdit          = "msg_edit"
	TypeMsgReaction      = "msg_reaction"
	TypePullOffline      = "pull_offline"
	TypePullOfflineChunk = "pull_offline_chunk"
	TypeError            = "error"
)

func NewFrame(t string, from string, to string, payload any) WireFrame {
	b, _ := json.Marshal(payload)
	return WireFrame{
		V:       1,
		Type:    t,
		ID:      uuid.NewString(),
		Ts:      time.Now().UnixMilli(),
		From:    from,
		To:      to,
		Payload: b,
	}
}

// ----- payload schemas -----

type EncryptedMessage struct {
	ClientMsgID     string  `json:"client_msg_id"`
	ConvID          string  `json:"conv_id"`
	ToDeviceID      string  `json:"to_device_id"`
	Kind            string  `json:"kind"` // = "encrypted"
	Alg             string  `json:"alg"`
	NonceB64        string  `json:"nonce_b64"`
	EphemeralPubB64 string  `json:"ephemeral_pub_b64"`
	CiphertextB64   string  `json:"ciphertext_b64"`
	AadB64          string  `json:"aad_b64,omitempty"`
	RefMsgID        *string `json:"ref_msg_id,omitempty"`
	Size            int     `json:"size"`
}

type ServerHello struct {
	ServerTime int64  `json:"server_time"`
	SessionID  string `json:"session_id"`
}

type MsgAck struct {
	ClientMsgID string `json:"client_msg_id"`
	ServerMsgID string `json:"server_msg_id,omitempty"`
	Status      string `json:"status"`
	Reason      string `json:"reason,omitempty"`
}

type MsgDelivered struct {
	ClientMsgID string `json:"client_msg_id"`
	ServerMsgID string `json:"server_msg_id"`
	ToDeviceID  string `json:"to_device_id"`
}

type PresenceSub struct {
	DeviceIDs []string `json:"device_ids"`
}

type PresenceUpdate struct {
	DeviceID string `json:"device_id"`
	State    string `json:"state"`
	LastSeen *int64 `json:"last_seen,omitempty"`
}

type PullOffline struct {
	SinceTs int64 `json:"since_ts"`
}

type PullOfflineChunk struct {
	Messages []WireFrame `json:"messages"`
	HasMore  bool        `json:"has_more"`
	Cursor   string      `json:"cursor,omitempty"`
}

type WireError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	RefID   string `json:"ref_id,omitempty"`
}
