# NearLink WebSocket 协议

> 版本：`v1`。所有帧 JSON 编码，UTF-8。服务器只看路由头，不解析 `ciphertext`。

## 1. 端点

| URL | 协议 | 说明 |
|-----|------|------|
| `wss://<host>/v1/ws` | WebSocket Secure | 主长连接 |
| `https://<host>/v1/auth/challenge` | HTTPS GET | 取登录挑战 |
| `https://<host>/v1/files/upload` | HTTPS POST (multipart) | 大文件分片可选 |
| `https://<host>/v1/files/{id}/{idx}` | HTTPS GET | 拉取分片 |
| `https://<host>/v1/health` | HTTPS GET | 健康检查 |

## 2. 认证流程（Ed25519 挑战-签名，无密码）

```
Client                                Server
  │  GET /v1/auth/challenge            │
  │   ?device_id=<id>                  │
  │ ───────────────────────────────► │
  │                                    │ 生成 nonce(32B), 缓存 60s
  │  200 { challenge_id, nonce_b64,    │
  │        server_time, expires_in }   │
  │ ◄─────────────────────────────── │
  │                                    │
  │  WS Upgrade /v1/ws                 │
  │  Header X-NL-Auth: <jwt-like>      │
  │   payload = base64({               │
  │     device_id, challenge_id,       │
  │     pubkey_b64, ts                 │
  │   })                               │
  │   sig = ed25519(pubkey, payload‖nonce) │
  │ ───────────────────────────────► │
  │                                    │ 校验 sig + sha256(pubkey)==device_id
  │  101 Switching Protocols           │
  │  → 第一帧 server_hello             │
  │ ◄─────────────────────────────── │
```

`X-NL-Auth` 实际格式：`<payload_b64>.<sig_b64>`。`device_id = base32(sha256(pubkey))[:24]`。

## 3. 帧（Envelope）

所有 WS 文本帧统一形如：

```json
{
  "v": 1,
  "type": "<frame_type>",
  "id": "<frame_uuid>",
  "ts": 1715400000000,
  "from": "<device_id>",
  "to":   "<device_id_or_null>",
  "payload": { ... }
}
```

`type` 取值：

| type | 方向 | payload 字段 | 说明 |
|------|------|--------------|------|
| `server_hello` | S→C | `server_time`, `session_id` | 升级后第一帧 |
| `ping` / `pong` | 双向 | `nonce` | 30s 心跳 |
| `presence_sub` | C→S | `device_ids: []` | 订阅联系人在线状态 |
| `presence_update` | S→C | `device_id`, `state`, `last_seen` | 状态推送 |
| `msg_send` | C→S | 见 §4 | 发送密文消息 |
| `msg_relay` | S→C | 同 `msg_send` payload | 服务器把对端的密文中转给你 |
| `msg_ack` | S→C | `client_msg_id`, `server_msg_id`, `status` | 服务器收到确认 |
| `msg_delivered` | S→C | `server_msg_id`, `to_device_id` | 对端在线已直推 |
| `msg_read` | 双向 | `conv_id`, `up_to_msg_id` | 已读回执 |
| `msg_typing` | 双向 | `conv_id`, `state: start\|stop` | 正在输入 |
| `msg_revoke` | 双向 | `target_msg_id` | 撤回（密文里也含） |
| `msg_edit` | 双向 | `target_msg_id`, `new_ciphertext` | 编辑 |
| `msg_reaction` | 双向 | `target_msg_id`, `emoji`, `op: add\|remove` | 表情回应 |
| `pull_offline` | C→S | `since_ts` | 拉取离线 |
| `pull_offline_chunk` | S→C | `messages: []`, `has_more`, `cursor` | 离线返回 |
| `error` | S→C | `code`, `message`, `ref_id` | 错误 |

## 4. `msg_send` payload

```json
{
  "client_msg_id": "01HABCD...",         // ULID/UUIDv7
  "conv_id": "<peer_device_id>",         // 单聊 = 对端 device_id
  "to_device_id": "<peer_device_id>",
  "kind": "encrypted",                   // 永远是 encrypted；type 由密文内部决定
  "alg":  "xchacha20poly1305",           // 或 aes-256-gcm
  "nonce_b64": "...",                    // 24B (XChaCha) / 12B (GCM)
  "ephemeral_pub_b64": "...",            // X25519 ephemeral
  "ciphertext_b64": "...",               // 含 type + body 的密封
  "aad_b64": "...",                      // = from‖to‖conv_id‖client_msg_id
  "ref_msg_id": null,                    // 回复引用（可空）
  "size": 1234                           // 仅用于配额/限流
}
```

服务器对 `msg_send` 的处理：
1. 校验 `from == 当前会话 device_id`，否则 1008 关连接。
2. 校验 `size <= cfg.MaxMessageBytes`（默认 64KiB；附件用文件 API）。
3. 限流：每 `device_id` 每秒 N 条。
4. `to_device_id` 在线 → 立即 `msg_relay` 给目标 + 给发送方 `msg_delivered`。
5. 不在线 → 落 `offline_queue` + 返回 `msg_ack(status="queued")`。

## 5. 离线拉取

客户端每次 `server_hello` 后：

```
→ pull_offline { since_ts: <last_synced_ts> }
← pull_offline_chunk { messages: [...], has_more: true, cursor: "..." }
（重复直到 has_more=false）
```

服务器交付后**立刻**从队列删除（at-most-once 由客户端 msg_id 唯一性兜底去重）。

## 6. 错误码

| code | 含义 |
|------|------|
| `400_bad_frame` | JSON 不合法或字段缺失 |
| `401_auth_failed` | 签名无效 / device_id 不匹配 |
| `403_forbidden` | 行为越权（伪造 from） |
| `404_unknown_peer` | 目标 device 从未连接过本服务器 |
| `409_duplicate_msg` | 同 client_msg_id 重复 |
| `413_too_large` | 超 MaxMessageBytes |
| `429_rate_limited` | 限流 |
| `503_overloaded` | 服务端过载 |
| `1008` | WS 关闭码：策略违规 |

## 7. 心跳与重连

- 客户端每 30s 发 `ping`，服务器 `pong`；任一端 90s 无收即 close。
- 客户端断线指数退避：`min(2^n, 60) * (1 + jitter)`。
- 重连后必须再走一次挑战-签名，**不复用** session token。

## 8. 大文件传输

详见 `bluetooth.md` 与本协议 `/v1/files/*`：
- 上传前先 WS 发 `file_init`（落在 `msg_send` 的 ciphertext 内部，对端预知 file_id 与总大小）。
- 文件分片走独立 HTTPS：每片 64KiB，整文件 SHA-256 在密文里端到端校验。
- 服务器仅按 (file_id, chunk_idx) 存盘，不读内容。
