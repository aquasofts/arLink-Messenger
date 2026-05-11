package storage

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/google/uuid"
	_ "github.com/jackc/pgx/v5/stdlib"
)

type pgStore struct {
	db *sql.DB
}

func OpenPostgres(dsn string) (Store, error) {
	db, err := sql.Open("pgx", dsn)
	if err != nil {
		return nil, err
	}
	if err := db.Ping(); err != nil {
		_ = db.Close()
		return nil, err
	}
	db.SetMaxOpenConns(20)
	return &pgStore{db: db}, nil
}

func (s *pgStore) UpsertDevice(ctx context.Context, deviceID string, pub []byte, lastSeen time.Time) error {
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO devices(device_id, pubkey, created_at, last_seen_ms)
		VALUES($1, $2, $3, $4)
		ON CONFLICT (device_id) DO UPDATE SET pubkey=EXCLUDED.pubkey, last_seen_ms=EXCLUDED.last_seen_ms`,
		deviceID, pub, time.Now().UnixMilli(), lastSeen.UnixMilli())
	return err
}

func (s *pgStore) GetDevicePub(ctx context.Context, deviceID string) ([]byte, error) {
	var pub []byte
	err := s.db.QueryRowContext(ctx, `SELECT pubkey FROM devices WHERE device_id=$1`, deviceID).Scan(&pub)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return pub, err
}

func (s *pgStore) UpdateLastSeen(ctx context.Context, deviceID string, ts time.Time) error {
	_, err := s.db.ExecContext(ctx, `UPDATE devices SET last_seen_ms=$1 WHERE device_id=$2`, ts.UnixMilli(), deviceID)
	return err
}

func (s *pgStore) EnqueueOffline(ctx context.Context, e Envelope) error {
	if e.ServerMsgID == "" {
		e.ServerMsgID = uuid.NewString()
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO offline_queue
		(id, client_msg_id, from_device_id, to_device_id, conv_id, alg, nonce_b64, eph_pub_b64, ciphertext_b64, ref_msg_id, size, created_at_ms)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
		ON CONFLICT (to_device_id, client_msg_id) DO NOTHING`,
		e.ServerMsgID, e.ClientMsgID, e.FromDeviceID, e.ToDeviceID, e.ConvID,
		e.Alg, e.NonceB64, e.EphPubB64, e.CiphertextB64, e.RefMsgID, e.Size, e.CreatedAt.UnixMilli())
	return err
}

func (s *pgStore) DrainOffline(ctx context.Context, toDeviceID string, since time.Time, limit int) ([]Envelope, error) {
	if limit <= 0 {
		limit = 200
	}
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, client_msg_id, from_device_id, to_device_id, conv_id, alg, nonce_b64, eph_pub_b64, ciphertext_b64, ref_msg_id, size, created_at_ms
		FROM offline_queue WHERE to_device_id=$1 AND created_at_ms >= $2 ORDER BY created_at_ms ASC LIMIT $3`,
		toDeviceID, since.UnixMilli(), limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []Envelope{}
	for rows.Next() {
		var e Envelope
		var createdMs int64
		var ref sql.NullString
		if err := rows.Scan(&e.ServerMsgID, &e.ClientMsgID, &e.FromDeviceID, &e.ToDeviceID, &e.ConvID,
			&e.Alg, &e.NonceB64, &e.EphPubB64, &e.CiphertextB64, &ref, &e.Size, &createdMs); err != nil {
			return nil, err
		}
		if ref.Valid {
			v := ref.String
			e.RefMsgID = &v
		}
		e.CreatedAt = time.UnixMilli(createdMs)
		out = append(out, e)
	}
	return out, rows.Err()
}

func (s *pgStore) DeleteOffline(ctx context.Context, ids []string) error {
	if len(ids) == 0 {
		return nil
	}
	_, err := s.db.ExecContext(ctx, `DELETE FROM offline_queue WHERE id = ANY($1)`, ids)
	return err
}

func (s *pgStore) GCOffline(ctx context.Context, before time.Time) (int, error) {
	res, err := s.db.ExecContext(ctx, `DELETE FROM offline_queue WHERE created_at_ms < $1`, before.UnixMilli())
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return int(n), nil
}

func (s *pgStore) PutFileChunkMeta(ctx context.Context, fileID string, idx, size int, sha256B64 string) error {
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO file_chunks(file_id, idx, size, sha256_b64) VALUES($1,$2,$3,$4)
		ON CONFLICT (file_id, idx) DO UPDATE SET size=EXCLUDED.size, sha256_b64=EXCLUDED.sha256_b64`,
		fileID, idx, size, sha256B64)
	return err
}

func (s *pgStore) HasFileChunk(ctx context.Context, fileID string, idx int) (bool, error) {
	var cnt int
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(1) FROM file_chunks WHERE file_id=$1 AND idx=$2`, fileID, idx).Scan(&cnt)
	return cnt > 0, err
}

func (s *pgStore) Close() error { return s.db.Close() }
