# NearLink Server

Go 实现的 WebSocket 中转服务器，仅路由密文 / 维护在线状态 / 离线缓存。**不解密任何消息内容**。

## 快速开始

```bash
go run ./cmd/nearlink-server -c config.example.yaml
# 或
make build && ./bin/nearlink-server -c config.example.yaml
```

## 编译

| 目标 | 命令 |
|------|------|
| 本地二进制 | `make build` → `bin/nearlink-server` |
| Docker 镜像 | `docker build -t nearlink/nearlink-server:dev .` |
| 一键起整套 | `docker compose up -d --build` |

## 目录结构

```
server/
├── cmd/nearlink-server/main.go      # 入口
├── internal/
│   ├── config/                      # YAML + 默认值
│   ├── logger/                      # zap 包装
│   ├── auth/                        # Ed25519 挑战签名
│   ├── websocket/                   # Hub / Upgrader / Client
│   ├── message/                     # (留作 v2 扩展点)
│   ├── presence/                    # 在线追踪
│   ├── ratelimit/                   # per-device 令牌桶
│   └── storage/                     # Store 接口 + sqlite/postgres 实现
└── migrations/                      # 0001_init / 0002_offline_queue
```

## 协议

详见仓库根目录 [docs/protocol.md](../docs/protocol.md)。要点：

- `GET /v1/auth/challenge?device_id=xxx` → JSON `{ challenge_id, nonce_b64, server_time, expires_in }`
- `GET /v1/ws` with `X-NL-Auth: <payload_b64>.<sig_b64>` → WebSocket 升级
- WS 文本帧 `WireFrame { v, type, id, ts, from, to, payload }`

## 不变量

- 服务器**只读** `from`、`to`、`conv_id`、`client_msg_id`、`size`、`ts`、`alg`。
- 服务器**不读** `ciphertext_b64`、`nonce_b64`、`ephemeral_pub_b64`。
- 服务器**不可能**伪造发送方：所有写入都先校验 `frame.from == 当前会话 device_id`。

## License

参考仓库根目录 LICENSE。
