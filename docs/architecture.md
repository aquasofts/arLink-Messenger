# NearLink Messenger — 总体架构

> 端到端加密、无手机号/账号的即时通讯。蓝牙近场直连 + 自建服务器密文中转双通道。

## 1. 项目目标

| # | 目标 | 说明 |
|---|------|------|
| 1 | 去账号化 | 不绑定手机号 / 邮箱，设备首启动即生成本地身份。 |
| 2 | 端到端加密 | 服务器永远拿不到明文（只见密文 + 路由头）。 |
| 3 | 近场直传 | 两台设备在蓝牙范围内可不依赖任何服务器互发消息。 |
| 4 | 远程中转 | 同一台自建服务器的两个联系人，离线时可异步收发密文。 |
| 5 | 类 Telegram 体验 | 文本/图片/文件/语音/回复/撤回/编辑/已读/输入中。 |

## 2. 顶层组件图

```
 ┌───────────────────────────────────────────────────────────────┐
 │                    Android App  (Kotlin/Compose)             │
 │                                                              │
 │  UI (Compose) ── ViewModel ── UseCase ── Repository          │
 │                                              │               │
 │                              ┌───────────────┼─────────────┐ │
 │                              ▼               ▼             ▼ │
 │                         RoomDb         CryptoEngine   TransportManager │
 │                                              │             │ │
 │                              ┌───────────────┴───────┐     │ │
 │                              ▼                       ▼     │ │
 │                       BluetoothEngine       WebSocketEngine│ │
 │                       (BLE + RFCOMM)         (OkHttp WSS)  │ │
 └──────────────┬───────────────────────────┬──────────────────┘
                │ BLE/L2CAP                 │ WSS over TLS
                ▼                           ▼
 ┌──────────────────────┐         ┌───────────────────────────────┐
 │ Peer Android Device  │         │  Go Server (nearlink-server)  │
 │  (same app)          │         │                               │
 └──────────────────────┘         │  Router ── WS Hub ── Presence │
                                  │     │         │         │     │
                                  │     ▼         ▼         ▼     │
                                  │  Storage   OfflineQueue  RateLimit │
                                  │     │                           │
                                  │     ▼                           │
                                  │  Postgres (+Redis optional)     │
                                  └───────────────────────────────┘
```

## 3. 分层职责

### 3.1 Android 客户端

| 层 | 包 | 职责 |
|----|----|------|
| Presentation | `ui/` | Compose UI + ViewModel（MVVM） |
| Domain | `domain/usecase` | 业务用例：发消息、加好友、撤回… |
| Data | `data/repository`, `data/local` | Room + DataStore + 仓库 |
| Crypto | `core/crypto` | 身份密钥、会话密钥派生、AEAD |
| Transport | `core/transport` | `TransportManager` 选择 BT 或 WS |
| Bluetooth | `core/bluetooth` | BLE 广播/扫描 + RFCOMM 数据通道 |
| Network | `core/network` | OkHttp WebSocket 长连接 |
| Service | `service/` | 前台服务：连接保持、消息接收 |
| Worker | `worker/` | WorkManager：重发、文件分片续传 |

### 3.2 Go 服务器

| 包 | 职责 |
|----|------|
| `cmd/nearlink-server` | 入口、HTTP 路由挂载、信号处理 |
| `internal/config` | 配置加载（env + yaml） |
| `internal/auth` | 客户端 Ed25519 签名挑战认证（无密码） |
| `internal/websocket` | WS Hub、连接生命周期、心跳 |
| `internal/message` | 路由密文包、ACK、离线入队 |
| `internal/presence` | 在线状态、最后在线时间 |
| `internal/storage` | Postgres/SQLite 持久层、文件分片元数据 |
| `internal/ratelimit` | 按 device_id 限流，防 abuse |
| `internal/logger` | zap 结构化日志，禁止打印密文 |

## 4. 身份与信任模型

- 设备身份 = **Ed25519 长期密钥对**（`device_id = base32(sha256(pubkey))[:24]`）
- 加好友 = 在蓝牙近场亲自校对 **安全码（4×5 数字组）**，等价于面对面 TOFU。
- 联系人变更密钥 = 视为新身份，UI 显示醒目警告，并要求重新确认。
- 服务器**不参与**信任决策，只做密文路由。

## 5. 端到端加密简述

详见 `encryption.md`。核心：

1. 双方握手生成会话：X25519 ECDH(staticA, staticB) → HKDF → root_key。
2. 发送侧每条消息一个 epheremal X25519 sender_ephemeral 与对端静态键再 ECDH，配合 root_key 派生 message_key（轻量"双棘轮 lite"）。
3. AEAD = AES-256-GCM（Tink 提供，纯 JVM 依赖）。XChaCha20-Poly1305 列入 roadmap。
4. AAD 包含 `sender_device_id || recipient_device_id || conv_id || msg_id`。
5. 服务器看到的字段：`from, to, conv_id, msg_id, ts, ciphertext_b64, nonce, type=encrypted`。

## 6. 传输选择策略（TransportManager）

伪代码：

```
fun pick(peer): Channel = when {
    isBluetoothConnected(peer)        -> BLUETOOTH       // 近场首选，无流量
    isServerConnected() && peer.online -> SERVER_DIRECT  // 走服务器，对方在线
    isServerConnected()               -> SERVER_OFFLINE  // 入服务器离线队列
    else                              -> LOCAL_QUEUE     // 攒在本地，等任意通道恢复
}
```

- 蓝牙优先：省流量、抗墙、低延迟。
- 同 msg_id 在多个通道收到时由本地数据库唯一约束去重。
- 任一通道送达 ACK 即标 `DELIVERED`。

## 7. 消息生命周期

```
 [Compose UI] ── send(text)
        │
        ▼
 [SendMessageUseCase]
   ├─ msg_id = uuidv7()         (单调 + 唯一)
   ├─ encrypt(payload, peer_pub)
   ├─ insert Room (status=PENDING)
   └─ TransportManager.send(envelope)
              │
   ┌──────────┴───────────┐
   ▼                      ▼
 BluetoothEngine     WebSocketEngine
   │  ACK                │  server_ack + delivered
   ▼                      ▼
 status=SENT/DELIVERED via Flow → UI
```

## 8. 离线 & 重试

- WorkManager `MessageRetryWorker`：5s/15s/60s/5m/30m 指数退避，封顶。
- 服务器 `OfflineQueue` 表保存最多 N 天密文，目标端上线后按 `since_ts` 拉取。
- 文件分片上传/下载：`FileChunkWorker`，按 64KB 分片，分片哈希 + 整体 SHA-256 校验。

## 9. 状态展示

设备状态分三态：
- `BT_ONLINE`：和该联系人当前有蓝牙会话。
- `SERVER_ONLINE`：双方都接入同一服务器。
- `OFFLINE`：以上都不满足，展示 `last_seen`。

## 10. 威胁模型（详见 security.md）

| 攻击者 | 可达 | 不可达 |
|--------|------|--------|
| 服务器运营者 | 元数据（from/to/时间/包大小） | 消息明文 |
| 网络中间人 | TLS 已防御 | — |
| 蓝牙窃听者 | 与目标设备同物理空间也只见密文 | 明文 |
| 设备被盗 | 离线本地 DB（如启用 SQLCipher，则需口令） | — |

## 11. 关键非功能性需求

- 离线优先：所有 UI 直接读 Room，网络/蓝牙仅做同步层。
- 单一事实源：消息状态由 Room + Flow 推动，UI 不直连传输层。
- 可测试：Crypto/Transport/Repository 全部接口化，单测可注入 fake。
- 可部署：服务器一键 `docker compose up -d`。

## 12. 输出阶段索引

| 阶段 | 内容 | 状态 |
|------|------|------|
| 1 | 架构 | 本文 |
| 2 | 目录结构 | 见 `directory-structure.md` 与真实文件 |
| 3 | 协议（WS/加密/蓝牙） | `protocol.md` / `encryption.md` / `bluetooth.md` |
| 4 | Android Gradle 配置 | `android/` |
| 5–13 | Android 各层代码 | 后续 |
| 14–16 | Go 服务器 + 迁移 + Docker | 后续 |
| 17–20 | 文档 / 测试 / 安全 / 路线图 | 后续 |
