-- 0002_offline_queue.up.sql

CREATE TABLE IF NOT EXISTS offline_queue (
    id              TEXT    PRIMARY KEY,
    client_msg_id   TEXT    NOT NULL,
    from_device_id  TEXT    NOT NULL,
    to_device_id    TEXT    NOT NULL,
    conv_id         TEXT    NOT NULL,
    alg             TEXT    NOT NULL,
    nonce_b64       TEXT    NOT NULL,
    eph_pub_b64     TEXT    NOT NULL,
    ciphertext_b64  TEXT    NOT NULL,
    ref_msg_id      TEXT,
    size            INTEGER NOT NULL,
    created_at_ms   BIGINT  NOT NULL
);

-- 取队列：按收件人 + 时间顺序。
CREATE INDEX IF NOT EXISTS idx_offline_to_ts ON offline_queue(to_device_id, created_at_ms);

-- 同一个收件人对同一 client_msg_id 只保留一条（由 at-most-once 语义保证，避免客户端重试把队列打爆）。
CREATE UNIQUE INDEX IF NOT EXISTS uniq_offline_msg ON offline_queue(to_device_id, client_msg_id);
