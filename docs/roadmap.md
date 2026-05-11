# 后续路线（roadmap）

按优先级排列。每项给出"为什么、怎么做、影响面"三点，便于评估排期。

---

## v1.1 —— 收尾 v1 的 TODO（1–2 周）

### 1. 文件分片完整实现
- **为什么**：当前 `FileChunkWorker` 是骨架，无法投入使用。
- **怎么做**：
  - 服务器 `cmd/main.go` 新增 `/v1/files/{file_id}/{idx}`（GET/PUT）端点；落 `file_chunks` 元数据 + 内容文件。
  - 客户端 `FileChunkWorker` 调用 OkHttp 上传/下载；并行度 4，断点续传基于 `HasFileChunk` 探测；整体 SHA-256 在密文内端到端校验。
- **影响**：图片/文件/语音消息真正可用。

### 2. 完整 androidx.security 依赖
- **为什么**：`IdentityKeyStore` 依赖 `androidx.security:security-crypto`，目前文档已注明但 `libs.versions.toml` 未声明。
- **怎么做**：加 `androidx-security-crypto = { module = "androidx.security:security-crypto", version = "1.1.0-alpha06" }` 并在 app 模块依赖。
- **影响**：编译警告消失，私钥落盘自动启用 EncryptedFile。

### 3. ULID/UUIDv7 替代 UUIDv4
- **为什么**：`CryptoEngine.newClientMsgId` 用 UUIDv4，按时间排序需依赖 `created_at_ms`；UUIDv7 时间前缀单调，有利于服务器侧分页。
- **怎么做**：引入 `com.github.f4b6a3:ulid-creator` 或自行实现 UUIDv7。
- **影响**：极少；消息表查询略快。

### 4. PresenceRepository 与 UI 接线
- **为什么**：`ContactsScreen` 当前硬编码 `OFFLINE`。
- **怎么做**：`ContactsViewModel` 注入 `PresenceRepository`，按列表项分别 `observe(deviceId)`。
- **影响**：联系人列表显示真实 BT/Server 状态。

---

## v2 —— 加密 / 协议加固（3–6 周）

### 5. 完整 Double Ratchet
- **为什么**：当前每条消息 ephemeral X25519 提供 PFS，但不抵抗 root_key 长期泄露。
- **怎么做**：移植 Signal Double Ratchet（chain key 推进 + DH ratchet）：
  - 状态字段：`dhSelf`、`dhPeer`、`rootKey`、`chainKeyS`、`chainKeyR`、`nS`、`nR`、`pN`、`skippedKeys`。
  - 接收方对乱序消息按 `pN` 缓存跳过键。
  - 状态机持久化到 `keys` 表（Keystore 包裹）。
- **影响**：长期密钥泄露 → 仍只能解出当前 chain 内有限消息（前向 + 后向安全）。

### 6. 受信预签名密钥 (Signed PreKeys)
- **为什么**：当前首次握手必须双方在线（RFCOMM）。
- **怎么做**：参考 X3DH，把"上传一次性 prekey 到服务器"加入，对端无需在线即可发起会话。
- **影响**：异步会话首消息可用；服务器存储 prekey bundle（公钥，不可解密）。

### 7. 协议版本协商
- **为什么**：未来要平滑升级握手算法。
- **怎么做**：`server_hello` 加 `supported_versions`，`X-NL-Auth` 加 `v`。`PlaintextEnvelope` 加 `version`。
- **影响**：现网客户端可平滑过渡，无强制升级。

---

## v2 —— 体验扩容（4–8 周）

### 8. 多设备同身份
- **为什么**：手机换了不想丢联系人。
- **怎么做**：
  - 主设备签发"子设备证书"（Ed25519 签 child_pub）。
  - 服务器同 device_id 下允许多 WebSocket 会话；消息按所有子设备广播。
  - 配对时把 device_id chain 而非单一 device_id 写入联系人。
- **影响**：协议 + UI + 服务器路由全改，列为重点项。

### 9. 群聊
- **为什么**：用户期望。
- **怎么做**：Sender Keys（与 Signal 群聊一致），每个新成员通过一对一会话拿到组密钥；服务器只见组 ciphertext。
- **影响**：`conversations.kind` 拆出 `group_members` 表；UI 拆分。

### 10. QR 配对（远程）
- **为什么**：不在同一房间时也要能加好友。
- **怎么做**：把 `pk_id + pk_x + 一次性 nonce + sig` 编进 QR；扫码方走 §7 协议版本对应的握手。
- **影响**：UI 加扫码屏；可与 deeplink 集成。

### 11. Push 提醒（FCM / UnifiedPush）
- **为什么**：长连接被系统 Doze 杀掉的兜底。
- **怎么做**：
  - FCM 通道：服务器收到 `msg_send` 时若目标 device 离线 → 发 silent push wake 客户端 → 客户端连上后拉 offline。
  - 隐私：push payload 只含 `device_id` hash 前 8 位，不含任何元数据。
- **影响**：服务器集成 FCM Sender；客户端配置 FCM token 上报。

### 12. 消息搜索（FTS4）
- **为什么**：聊天多了找不到。
- **怎么做**：Room 加 `messages_fts` 虚表，索引 `text` 列；UI 加搜索屏。
- **影响**：DB 多约 20%，搜索 O(log n)。

---

## v2 —— 运维 / 可观测性（持续）

### 13. Prometheus exporter
- 服务器暴露 `/metrics`：在线连接数、转发 QPS、离线队列长度、p50/p99 延迟。
- 字段命名严格遵守 `nearlink_*`。

### 14. OpenTelemetry tracing
- HTTP 入口与 WS dispatch 加 span；通过 OTLP 输出。
- 永不把 ciphertext 字段写入 span 属性。

### 15. 水平扩容
- 把 Hub 内存路由换成 Redis Pub/Sub：每实例订阅 `nl:route:{device_id}` channel。
- Auth challenge 与 ratelimit 移到 Redis 原子计数。
- 详见 [server-deploy.md §8](server-deploy.md)。

---

## v3 —— 探索方向（>3 个月）

- **Wi-Fi Direct / Wi-Fi Aware**：蓝牙带宽不够时切上去，支持大文件高速直传。
- **去中心化路由**：基于 libp2p 的中转节点联邦，用户自带服务器加入网状网。
- **桌面客户端**：Compose Multiplatform，复用 `core/crypto` 与 `core/transport` 模块。
- **Web 客户端**：用 WebAssembly 跑 libsodium；密钥放浏览器 IndexedDB + WebCrypto。
- **可审计构建（reproducible builds）**：发布 APK 与 server 二进制都需可复现。

---

## 不计划做的事

- **手机号绑定** —— 与项目核心理念冲突。
- **服务器侧 KMS / 中央密钥托管** —— 任何"找回密钥"机制都会破坏 E2EE 保证。
- **广告 / SDK 接入** —— 隐私一票否决。
