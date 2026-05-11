package storage

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/google/uuid"
	_ "github.com/mattn/go-sqlite3"
)

type sqliteStore struct {
	db *sql.DB
}

func OpenSQLite(dsn string) (Store, error) {
	db, err := sql.Open("sqlite3", dsn)
	if err != nil {
		return nil, err
	}
	if err := db.Ping(); err != nil {
		_ = db.Close()
		return nil, err
	}
	db.SetMaxOpenConns(1) // sqlite 单写者
	s := &sqliteStore{db: db}
	if err := s.migrate(context.Background()); err != nil {
		_ = db.Close()
		return nil, err
	}
	return s, nil
}

func (s *sqliteStore) migrate(ctx context.Context) error {
	stmts := []string{
		`CREATE TABLE IF NOT EXISTS devices (
			device_id     TEXT PRIMARY KEY,
			pubkey        BLOB NOT NULL,
			created_at    INTEGER NOT NULL,
			last_seen_ms  INTEGER NOT NULL
		)`,
		`CREATE TABLE IF NOT EXISTS offline_queue (
			id              TEXT PRIMARY KEY,
			client_msg_id   TEXT NOT NULL,
			from_device_id  TEXT NOT NULL,
			to_device_id    TEXT NOT NULL,
			conv_id         TEXT NOT NULL,
			alg             TEXT NOT NULL,
			nonce_b64       TEXT NOT NULL,
			eph_pub_b64     TEXT NOT NULL,
			ciphertext_b64  TEXT NOT NULL,
			ref_msg_id      TEXT,
			size            INTEGER NOT NULL,
			created_at_ms   INTEGER NOT NULL
		)`,
		`CREATE INDEX IF NOT EXISTS idx_offline_to_ts ON offline_queue(to_device_id, created_at_ms)`,
		`CREATE UNIQUE INDEX IF NOT EXISTS uniq_offline_msg ON offline_queue(to_device_id, client_msg_id)`,
		`CREATE TABLE IF NOT EXISTS file_chunks (
			file_id     TEXT NOT NULL,
			idx         INTEGER NOT NULL,
			size        INTEGER NOT NULL,
			sha256_b64  TEXT NOT NULL,
			PRIMARY KEY (file_id, idx)
		)`,
	}
	for _, q := range stmts {
		if _, err := s.db.ExecContext(ctx, q); err != nil {
			return err
		}
	}
	return nil
}

func (s *sqliteStore) UpsertDevice(ctx context.Context, deviceID string, pub []byte, lastSeen time.Time) error {
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO devices(device_id, pubkey, created_at, last_seen_ms)
		VALUES(?, ?, ?, ?)
		ON CONFLICT(device_id) DO UPDATE SET pubkey=excluded.pubkey, last_seen_ms=excluded.last_seen_ms`,
		deviceID, pub, time.Now().UnixMilli(), lastSeen.UnixMilli())
	return err
}

func (s *sqliteStore) GetDevicePub(ctx context.Context, deviceID string) ([]byte, error) {
	var pub []byte
	err := s.db.QueryRowContext(ctx, `SELECT pubkey FROM devices WHERE device_id=?`, deviceID).Scan(&pub)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	return pub, err
}

func (s *sqliteStore) UpdateLastSeen(ctx context.Context, deviceID string, ts time.Time) error {
	_, err := s.db.ExecContext(ctx, `UPDATE devices SET last_seen_ms=? WHERE device_id=?`, ts.UnixMilli(), deviceID)
	return err
}

func (s *sqliteStore) EnqueueOffline(ctx context.Context, e Envelope) error {
	if e.ServerMsgID == "" {
		e.ServerMsgID = uuid.NewString()
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT OR IGNORE INTO offline_queue
		(id, client_msg_id, from_device_id, to_device_id, conv_id, alg, nonce_b64, eph_pub_b64, ciphertext_b64, ref_msg_id, size, created_at_ms)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		e.ServerMsgID, e.ClientMsgID, e.FromDeviceID, e.ToDeviceID, e.ConvID,
		e.Alg, e.NonceB64, e.EphPubB64, e.CiphertextB64, e.RefMsgID, e.Size, e.CreatedAt.UnixMilli())
	return err
}

func (s *sqliteStore) DrainOffline(ctx context.Context, toDeviceID string, since time.Time, limit int) ([]Envelope, error) {
	if limit <= 0 {
		limit = 200
	}
	rows, err := s.db.QueryContext(ctx, `
		SELECT id, client_msg_id, from_device_id, to_device_id, conv_id, alg, nonce_b64, eph_pub_b64, ciphertext_b64, ref_msg_id, size, created_at_ms
		FROM offline_queue WHERE to_device_id=? AND created_at_ms >= ? ORDER BY created_at_ms ASC LIMIT ?`,
		toDeviceID, since.UnixMilli(), limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []Envelope{}
	for rows.Next() {
		var e Envelope
		var createdMs int64
		var refMsg sql.NullString
		if err := rows.Scan(&e.ServerMsgID, &e.ClientMsgID, &e.FromDeviceID, &e.ToDeviceID, &e.ConvID,
			&e.Alg, &e.NonceB64, &e.EphPubB64, &e.CiphertextB64, &refMsg, &e.Size, &createdMs); err != nil {
			return nil, err
		}
		if refMsg.Valid {
			s := refMsg.String
			e.RefMsgID = &s
		}
		e.CreatedAt = time.UnixMilli(createdMs)
		out = append(out, e)
	}
	return out, rows.Err()
}

func (s *sqliteStore) DeleteOffline(ctx context.Context, ids []string) error {
	if len(ids) == 0 {
		return nil
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	stmt, err := tx.PrepareContext(ctx, `DELETE FROM offline_queue WHERE id=?`)
	if err != nil {
		return err
	}
	defer stmt.Close()
	for _, id := range ids {
		if _, err := stmt.ExecContext(ctx, id); err != nil {
			return err
		}
	}
	return tx.Commit()
}

func (s *sqliteStore) GCOffline(ctx context.Context, before time.Time) (int, error) {
	res, err := s.db.ExecContext(ctx, `DELETE FROM offline_queue WHERE created_at_ms < ?`, before.UnixMilli())
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return int(n), nil
}

func (s *sqliteStore) PutFileChunkMeta(ctx context.Context, fileID string, idx, size int, sha256B64 string) error {
	_, err := s.db.ExecContext(ctx, `
		INSERT OR REPLACE INTO file_chunks(file_id, idx, size, sha256_b64) VALUES(?,?,?,?)`,
		fileID, idx, size, sha256B64)
	return err
}

func (s *sqliteStore) HasFileChunk(ctx context.Context, fileID string, idx int) (bool, error) {
	var cnt int
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(1) FROM file_chunks WHERE file_id=? AND idx=?`, fileID, idx).Scan(&cnt)
	return cnt > 0, err
}

func (s *sqliteStore) Close() error { return s.db.Close() }
