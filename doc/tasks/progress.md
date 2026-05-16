# 总体进度

本目录记录对 NearLink Messenger 的代码审查后拆分出的修复与优化任务。任务按模块拆分到独立文档，使用 checklist 表示完成状态。

## 模块文档

- [Android 任务清单](android.md)
- [Server 任务清单](server.md)

## 当前总体状态

| 模块 | 已完成 | 总任务 | 进度 | 说明 |
|---|---:|---:|---:|---|
| Android | 0 | 18 | 0% | 重点是 ACK 状态机、离线同步、presence 接线、SQLCipher、文件分片与密钥变化告警。 |
| Server | 0 | 16 | 0% | 重点是 presence 订阅清理、离线删除确认语义、payload 校验、存储一致性与队列上限。 |
| 合计 | 0 | 34 | 0% | 当前仅完成审查与设计拆分，尚未开始代码修复。 |

## 优先级建议

### P0：先修会导致消息丢失或状态错误的问题

- [ ] Server：修正离线消息 flush 的删除确认语义。
- [ ] Android：接入离线拉取 chunk 到消息入库并发送确认。
- [ ] Android：避免 Repository 状态更新与异步 ACK 脱节。
- [ ] Android：让 `WebSocketEngine.send` 等待服务器 ACK 或明确只返回 attempt 结果。
- [ ] Server：增强 `msg_send` 输入校验。

### P1：修安全与资源泄漏问题

- [ ] Server：修复 presence 订阅泄漏。
- [ ] Android：对解密失败显式标记联系人密钥变化。
- [ ] Android：完成 SQLCipher 开关或删除半成品入口。
- [ ] Android：减少敏感运行时日志。
- [ ] Server：为 challenge store 增加容量限制与定期 GC。
- [ ] Server：实现 ratelimit bucket GC。

### P2：补齐文档承诺功能

- [ ] Android：将 `PresenceRepository` 真正接到 WebSocket presence 事件。
- [ ] Android：联系人列表显示真实 presence。
- [ ] Android：启动后自动订阅联系人 presence。
- [ ] Android：实现 `FileChunkWorker`。
- [ ] Server：为 file chunk 增加内容存储接口。
- [ ] Android：将附件发送纳入 outbox 与状态机。

### P3：一致性、可维护性、运维增强

- [ ] Server：修复 Postgres `DeleteOffline` 参数绑定。
- [ ] Server：统一 SQLite 运行时迁移与 SQL 迁移文件。
- [ ] Server：落实 `offline.max_per_to` 队列上限。
- [ ] Android：将 client message id 从 UUIDv4 升级为 ULID/UUIDv7。
- [ ] Android：调整通道优先级与文档一致。
- [ ] Server：增加可观测性基础。
- [ ] Server：优化启动错误处理。

## 详细设计总览

### 消息投递状态机

1. UI 调用 `MessageRepository.sendText`，本地插入 `PENDING` 消息与 outbox。
2. `TransportManager` 只负责选择可用通道并执行一次发送尝试。
3. WebSocket 写入成功不等于服务端已收；服务端 `msg_ack` 才能把状态推进到 queued/relayed/rejected。
4. `MessageRepository` 应有单一 ACK collector，消费 BT/LAN/WS 合并 ACK，统一更新 Room 与 outbox。
5. delivered/read/edit/revoke 等异步事件也走同一入口，避免发送函数内局部 collect 导致状态遗漏。

### 离线同步协议

1. 客户端连接成功后按本地 cursor/since_ts 发送 `pull_offline`。
2. 服务端返回 `pull_offline_chunk`，但不立即删除队列。
3. 客户端逐条解密、去重、入库；成功后按 `server_msg_id` 批量 ack。
4. 服务端收到 ack 后删除对应离线记录。
5. 客户端保存 cursor/since_ts，重连后可安全重放；本地消息表唯一约束负责去重。

### Presence 设计

1. WebSocket 连接建立后，客户端订阅当前联系人 device_id 集合。
2. 服务端为每个连接维护订阅生命周期，断开时清理。
3. Android `PresenceRepository` 合并 BT session 与 server presence：BT 在线优先，其次 Server 在线，否则 Offline + last_seen。
4. UI 使用合成后的 `ContactListItem` 展示状态，避免硬编码 OFFLINE。

### 文件分片设计

1. 附件先本地切片并计算整体 SHA-256。
2. 分片内容先由客户端端到端加密，再上传服务器；服务器只存密文、大小、sha256 元数据。
3. metadata 消息通过现有消息通道发送，包含 file_id、mime、size、sha256。
4. 接收方按 metadata 下载分片，校验整体 hash 后写入本地 attachment store。
5. 上传/下载进度由 WorkManager 持久化，失败可重试与断点续传。

## 验收顺序

- [ ] 先跑 `cd server && go test ./...`，修复服务端单测。
- [ ] 再跑 `cd android && ./gradlew :app:testDebugUnitTest`，修复客户端单测。
- [ ] 两台设备/模拟器执行 `docs/testing.md` 的 T1–T4。
- [ ] 文件分片完成后执行 T5。
- [ ] 每次涉及安全/日志变更后执行 `docs/security.md` 的人工 grep 检查。
