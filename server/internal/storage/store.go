package storage

import (
	"context"
	"time"
)

// Envelope 是密文消息在服务器侧的表示。服务器只看路由头，不读 Ciphertext。
type Envelope struct {
	ServerMsgID   string
	ClientMsgID   string
	FromDeviceID  string
	ToDeviceID    string
	ConvID        string
	Alg           string
	NonceB64      string
	EphPubB64     string
	CiphertextB64 string
	RefMsgID      *string
	Size          int
	CreatedAt     time.Time
}

// Store 是服务器需要的全部持久化接口。SQLite 与 Postgres 各实现一份。
type Store interface {
	// 设备
	UpsertDevice(ctx context.Context, deviceID string, pubKey []byte, lastSeen time.Time) error
	GetDevicePub(ctx context.Context, deviceID string) ([]byte, error)
	UpdateLastSeen(ctx context.Context, deviceID string, ts time.Time) error

	// 离线队列
	EnqueueOffline(ctx context.Context, e Envelope) error
	DrainOffline(ctx context.Context, toDeviceID string, since time.Time, limit int) ([]Envelope, error)
	DeleteOffline(ctx context.Context, ids []string) error
	GCOffline(ctx context.Context, before time.Time) (int, error)

	// 文件分片元数据（仅密文长度 / sha256，不读内容）
	PutFileChunkMeta(ctx context.Context, fileID string, idx int, size int, sha256B64 string) error
	HasFileChunk(ctx context.Context, fileID string, idx int) (bool, error)

	Close() error
}
