-- 0001_init.up.sql
-- Postgres dialect. SQLite 使用 internal/storage/sqlite.go 中的等价 DDL（运行时 CREATE IF NOT EXISTS）。
-- 生产环境建议用 Postgres + migrate 工具（golang-migrate）执行。

CREATE TABLE IF NOT EXISTS devices (
    device_id     TEXT        PRIMARY KEY,
    pubkey        BYTEA       NOT NULL,
    created_at    BIGINT      NOT NULL,
    last_seen_ms  BIGINT      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_devices_last_seen ON devices(last_seen_ms);

CREATE TABLE IF NOT EXISTS auth_challenges (
    challenge_id  TEXT        PRIMARY KEY,
    device_id     TEXT        NOT NULL,
    nonce         BYTEA       NOT NULL,
    created_at_ms BIGINT      NOT NULL,
    expires_at_ms BIGINT      NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_auth_challenges_expires ON auth_challenges(expires_at_ms);

CREATE TABLE IF NOT EXISTS file_chunks (
    file_id     TEXT    NOT NULL,
    idx         INTEGER NOT NULL,
    size        INTEGER NOT NULL,
    sha256_b64  TEXT    NOT NULL,
    uploaded_at BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (file_id, idx)
);
