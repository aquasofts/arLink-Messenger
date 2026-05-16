# Android 任务清单

依据 `README.md`、`docs/architecture.md`、`docs/bluetooth.md`、`docs/encryption.md`、`docs/security.md`、`docs/testing.md`、`docs/roadmap.md` 与当前 Android 代码审查整理。当前文档只列待修复/待优化项，未直接修改业务代码。

## 设计原则

- UI 只读 Room/DataStore/Flow，网络、蓝牙、LAN 作为同步层，不直接成为 UI 单一事实源。
- 所有消息内容、控制消息、附件元数据都保持端到端加密；服务器只见路由头与密文大小。
- 传输层返回“本次发送尝试”的即时结果，最终 delivered/failed 通过统一 ACK 流更新 Room。
- 生命周期内的长连接、蓝牙扫描、订阅和 WorkManager 必须可取消、可恢复、无重复收集。

## 加密 / 身份 / 本地存储

- [x] 移除未实现的 `CryptoEngine.open` 或改成显式参数 API
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/core/crypto/CryptoEngine.kt:74`
  - 问题：公开方法始终抛 `UnsupportedOperationException`，调用方误用会直接崩溃。
  - 详细设计：删除该方法，仅保留 `openWithPeer`；或将 peer static X25519 公钥作为必填参数；补单测确保 repository 只调用可用 API。

- [x] 将 client message id 从 UUIDv4 升级为 ULID/UUIDv7
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/core/crypto/CryptoEngine.kt:111`、`docs/architecture.md:117`
  - 问题：文档要求 uuidv7，当前 UUIDv4 不利于分页和按时间排序。
  - 详细设计：引入 UUIDv7/ULID 生成器；保持字符串长度和 Room 主键兼容；增加单调性与唯一性测试。

- [x] 完成 SQLCipher 开关或删除半成品入口
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/core/database/SqlCipherSupport.kt:34`
  - 问题：`setEnabled(true)` 后 `applyIfEnabled` 仍不加密 DB，用户会误以为本地库已保护。
  - 详细设计：接入 `net.zetetic:sqlcipher-android` 与 `SupportOpenHelperFactory`；设置页要求用户明确设置 passphrase；迁移明文 DB 到加密 DB；若短期不做，则隐藏 UI 开关并在文档标注默认明文 Room。

- [x] 删除 `ContactEntity.unused()` 兼容 hack
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/core/database/SqlCipherSupport.kt:51`
  - 问题：为防 IDE 删除依赖引入无意义扩展函数，增加误导。
  - 详细设计：移除该 import/函数；用真实依赖或测试防止误删。

## WebSocket / 离线同步

- [x] 让 `WebSocketEngine.send` 等待服务器 ACK 或明确只返回 queued attempt
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/core/network/WebSocketEngine.kt:232`、`MessageRepository.kt:154`
  - 问题：`send` 在 socket.send 成功后立即 emit `Sent`，真正 `msg_ack` 异步到 `ackEvents`，但 `MessageRepository.attempt` 没统一收集 ACK 流，状态可能提前变为 SENT 且 outbox 未正确清理。
  - 详细设计：方案 A：`send` 用 `clientMsgId` 等待 ackFlow 首个匹配结果并超时；方案 B：Repository 启动单一 ACK collector 处理 `TransportManager.ackEvents()`，`send` 只表示“已写入 socket”。二者选一，避免双重状态来源。

- [x] 接入离线拉取 chunk 到消息入库
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/core/network/WebSocketEngine.kt:221`、`worker/OfflinePullWorker.kt`
  - 问题：`pull_offline_chunk` 只 emit 到 flow，缺少稳定的 service/worker 消费链路与 ack 删除协议。
  - 详细设计：ForegroundService 或 OfflinePullWorker 收集 chunk，逐条转成 Envelope 调用 `MessageRepository.ingest`；入库成功后发送离线 ack；保存 cursor/since_ts 到 DataStore。

- [x] 减少敏感运行时日志
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/core/network/WebSocketEngine.kt:103`、`BluetoothEngine.kt:127`
  - 问题：日志包含异常文本、peer device id、帧类型；虽然不直接打印密文，但 release 中应默认更克制。
  - 详细设计：封装安全 logger；release 只记录短 device id 和错误分类；禁止记录 auth header、ciphertext、nonce、pubkey、完整 peer id。

- [x] 统一默认服务器 URL 与明文开发地址策略
  - 位置：`android/app/build.gradle.kts:31`、`AndroidManifest.xml:64`
  - 问题：Manifest 禁止 cleartext，但 README 建议开发使用 `ws://10.0.2.2:8080/v1/ws`，debug 可能连不上本地明文 WS。
  - 详细设计：debug 使用 network security config 仅允许 `10.0.2.2/localhost` 明文；release 保持禁止；设置页对 `ws://` 给出 debug-only 提示。

## Presence / UI 状态

- [x] 将 `PresenceRepository` 真正接到 WebSocket presence 事件
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/data/repository/PresenceRepository.kt:23`
  - 问题：init 注释说由 service collect，但仓库自身没有收集，若 service 未启动或重复启动，状态不可靠。
  - 详细设计：在 Repository 内收集 `ws.observePresence()` 并更新 `serverPresence`；或明确由单例 ForegroundService 管理，并保证只启动一个 collector。

- [x] 联系人列表显示真实 presence
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/ui/screens/contacts/ContactsViewModel.kt:13`、`docs/roadmap.md:25`
  - 问题：ViewModel 只暴露 contacts，没有按联系人合并 presence；roadmap 已记录 ContactsScreen 硬编码 OFFLINE。
  - 详细设计：定义 `ContactListItem(contact, presence)`；ViewModel 对每个 contact 组合 `PresenceRepository.observe(deviceId)`；UI 使用 `PresenceDot` 展示 BT/Server/Offline/last_seen。

- [x] 启动后自动订阅联系人 presence
  - 位置：`WebSocketEngine.kt:267`、`ContactRepository.kt`
  - 问题：有 subscribePresence 方法，但缺少“联系人列表变化 → 订阅集合更新”的统一流程。
  - 详细设计：在 service 或 repository 中收集联系人 deviceId 列表，去重后 debounce，连接成功后发送 `presence_sub`。

## Transport / 消息状态

- [x] 调整通道优先级与文档一致
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/core/transport/TransportManager.kt:81`、`docs/architecture.md:98`
  - 问题：代码优先 LAN，再蓝牙，再服务器；文档写蓝牙优先。LAN 是新增能力但未在 README 目标里说明。
  - 详细设计：确定最终策略：若保留 LAN，更新文档为 LAN/BT/Server 三通道；否则改为 BT > Server，并把 LAN 作为可选实验通道。

- [x] 避免 Repository 状态更新与异步 ACK 脱节
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/data/repository/MessageRepository.kt:154`
  - 问题：`attempt()` 只 collect 当前 transport.send() flow，不监听 `TransportManager.ackEvents()`，delivered/read 等异步事件可能丢失。
  - 详细设计：在 `MessageRepository` init 中启动 ACK collector；按 `clientMsgId` 更新状态、删除 outbox、记录 deliveredAt；发送 attempt 只负责调度和初始失败。

- [x] 对解密失败显式标记联系人密钥变化
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/data/repository/MessageRepository.kt:190`、`docs/security.md:46`
  - 问题：文档要求公钥变化告警，代码解密失败只返回 false，未更新 trust state。
  - 详细设计：区分未知发送者、AAD mismatch、AEAD failure；对已配对联系人连续解密失败标记 `CHANGED` 或 `SUSPICIOUS`，UI 要求重新确认安全码。

## 文件 / 图片 / 语音

- [x] 实现 `FileChunkWorker`
  - 位置：`android/app/src/main/kotlin/com/nearlink/messenger/worker/FileChunkWorker.kt:21`、`docs/roadmap.md:8`
  - 问题：当前 worker 直接 `Result.success()`，文件/图片/语音实际不可用。
  - 详细设计：inputData 包含 file_id、local_uri、direction、chunk_idx；64KiB 分片；先 E2EE 后上传；失败返回 retry；下载后整体 SHA-256 校验并更新 messages 附件本地路径。

- [x] 将附件发送纳入 outbox 与状态机
  - 位置：`MessageRepository.kt`、`AttachmentStore.kt`、`FileChunker.kt`
  - 问题：消息模型有附件字段，但缺少端到端发送、上传、下载、预览的完整闭环。
  - 详细设计：先发送附件 metadata 消息，后台上传密文分片；接收方收到 metadata 后按需下载；消息状态区分 metadata delivered 与 attachment downloaded。

## 测试清单

- [x] `CryptoEngine` 删除/替换 `open` 后补编译与单测。
- [ ] WebSocket ACK 状态机测试：queued/relayed/rejected/delivered。
- [ ] OfflinePullWorker 集成测试：chunk → ingest → ack。
- [ ] PresenceRepository 测试：BT 在线优先、Server 在线、lastSeen。
- [ ] FileChunkWorker 测试：断点续传、sha256 错误失败、重试。
- [ ] Debug/release cleartext 策略测试或手工验证。
