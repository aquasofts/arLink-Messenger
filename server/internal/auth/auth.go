package auth

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base32"
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
	"sync"
	"time"
)

// Challenger 颁发 nonce 并校验签名（无密码登录）。
//
// 状态：单实例进程内 sync.Map；多实例部署请用 Redis（参见 README 部署指引）。
type Challenger struct {
	ttl   time.Duration
	mu    sync.Mutex
	store map[string]challengeEntry
}

type challengeEntry struct {
	nonce     []byte
	deviceID  string
	createdAt time.Time
}

type Challenge struct {
	ChallengeID string `json:"challenge_id"`
	NonceB64    string `json:"nonce_b64"`
	ServerTime  int64  `json:"server_time"`
	ExpiresIn   int    `json:"expires_in"`
}

type authPayload struct {
	DeviceID    string `json:"device_id"`
	ChallengeID string `json:"challenge_id"`
	PubkeyB64   string `json:"pubkey_b64"`
	Ts          int64  `json:"ts"`
}

var b64 = base64.RawURLEncoding

func New(ttl time.Duration) *Challenger {
	return &Challenger{ttl: ttl, store: map[string]challengeEntry{}}
}

// Issue 颁发一个 challenge。会顺手清理过期。
func (c *Challenger) Issue(deviceID string) (Challenge, error) {
	if len(deviceID) < 8 {
		return Challenge{}, errors.New("device_id too short")
	}
	nonce := make([]byte, 32)
	if _, err := rand.Read(nonce); err != nil {
		return Challenge{}, err
	}
	id := newID()
	c.mu.Lock()
	c.gc(time.Now())
	c.store[id] = challengeEntry{nonce: nonce, deviceID: deviceID, createdAt: time.Now()}
	c.mu.Unlock()
	return Challenge{
		ChallengeID: id,
		NonceB64:    b64.EncodeToString(nonce),
		ServerTime:  time.Now().UnixMilli(),
		ExpiresIn:   int(c.ttl.Seconds()),
	}, nil
}

// Verify 校验 X-NL-Auth 头："<payload_b64>.<sig_b64>"。
// 成功时返回 device_id 和原始 pubkey。
func (c *Challenger) Verify(header string, clockSkew time.Duration) (string, []byte, error) {
	parts := strings.SplitN(header, ".", 2)
	if len(parts) != 2 {
		return "", nil, errors.New("bad header")
	}
	payloadBytes, err := b64.DecodeString(parts[0])
	if err != nil {
		return "", nil, errors.New("bad payload b64")
	}
	sig, err := b64.DecodeString(parts[1])
	if err != nil {
		return "", nil, errors.New("bad sig b64")
	}
	var p authPayload
	if err := json.Unmarshal(payloadBytes, &p); err != nil {
		return "", nil, errors.New("bad payload json")
	}
	// 时钟漂移容忍
	now := time.Now().UnixMilli()
	if abs64(now-p.Ts) > clockSkew.Milliseconds() {
		return "", nil, errors.New("clock skew")
	}
	c.mu.Lock()
	entry, ok := c.store[p.ChallengeID]
	if ok {
		delete(c.store, p.ChallengeID) // one-time
	}
	c.mu.Unlock()
	if !ok {
		return "", nil, errors.New("no such challenge")
	}
	if entry.deviceID != p.DeviceID {
		return "", nil, errors.New("device mismatch")
	}
	if time.Since(entry.createdAt) > c.ttl {
		return "", nil, errors.New("challenge expired")
	}

	pub, err := b64.DecodeString(p.PubkeyB64)
	if err != nil || len(pub) != ed25519.PublicKeySize {
		return "", nil, errors.New("bad pubkey")
	}
	// device_id 必须等于 base32(sha256(pub))[:24] 小写无 padding
	if expected := DeviceIDFromPubKey(pub); expected != p.DeviceID {
		return "", nil, errors.New("device_id mismatch with pubkey")
	}

	msg := append(append([]byte{}, payloadBytes...), entry.nonce...)
	if !ed25519.Verify(pub, msg, sig) {
		return "", nil, errors.New("bad signature")
	}
	return p.DeviceID, pub, nil
}

func (c *Challenger) gc(now time.Time) {
	for k, v := range c.store {
		if now.Sub(v.createdAt) > c.ttl {
			delete(c.store, k)
		}
	}
}

// DeviceIDFromPubKey 与 Android 客户端 IdentityKeyStore 一致：
//   device_id = base32(sha256(pub))[:24] 小写无 padding。
func DeviceIDFromPubKey(pub []byte) string {
	sum := sha256.Sum256(pub)
	enc := strings.ToLower(base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(sum[:]))
	if len(enc) > 24 {
		enc = enc[:24]
	}
	return enc
}

func newID() string {
	var b [16]byte
	_, _ = rand.Read(b[:])
	return b64.EncodeToString(b[:])
}

func abs64(v int64) int64 {
	if v < 0 {
		return -v
	}
	return v
}
