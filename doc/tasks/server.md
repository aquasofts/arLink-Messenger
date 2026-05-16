# Server 任务清单

依据 `README.md`、`docs/architecture.md`、`docs/protocol.md`、`docs/security.md`、`docs/testing.md` 与当前 Go 代码审查整理。当前文档只列待修复/待优化项，未直接修改业务代码。

## 设计原则

- 服务器只做认证、在线状态、密文路由、离线队列与文件密文分片存取，不解密、不记录密文内容。
- 所有 WebSocket 消息必须先通过 device_id 与 Ed25519 公钥一致性校验，之后任何 `from` 字段都只能等于当前会话设备。
- 离线队列应保证“至少一次送达 + 客户端去重”，删除时机要避免发送成功但落库/客户端处理失败造成消息丢失。
- 存储层 SQLite/Postgres 行为必须一致，迁移与运行时 schema 必须一致。

## WebSocket / Hub

- [ ] 修复 presence 订阅泄漏
  - 位置：`server/internal/websocket/client.go:187`、`server/internal/presence/presence.go:63`
  - 问题：每次 `presence_sub` 都启动 goroutine 消费 channel，但连接关闭时没有调用 `Unsubscribe`，重复订阅也会覆盖 target 到同一个 channel，旧 goroutine 可能永久等待。
  - 详细设计：在 `Client` 上保存订阅取消函数或在 `readLoop` defer 中调用 `presence.Unsubscribe(c.deviceID)`；`Presence.Subscribe` 返回只读事件流的同时提供取消接口；重复订阅先清理旧订阅再建立新订阅。

- [ ] 修正离线消息 flush 的删除确认语义
  - 位置：`server/internal/websocket/hub.go:144`
  - 问题：`FlushOffline` 在服务端成功写 WS 后立即 `DeleteOffline`，客户端如果收到 chunk 后崩溃或未入库，消息会永久丢失。
  - 详细设计：改为“发送 chunk → 客户端逐条/批量 ack → 服务端删除已确认 id”。协议新增 `offline_ack` 或复用 `msg_ack` 带 `server_msg_id`；旧的 `pull_offline_chunk` 只负责投递，不负责删除。

- [ ] 增强 `msg_send` 输入校验
  - 位置：`server/internal/websocket/client.go:171`、`server/internal/websocket/frame.go:55`
  - 问题：当前未校验 `to_device_id`、`client_msg_id`、`conv_id`、`alg`、base64 字段、`size` 与实际密文长度关系，异常帧会进入路由/存储。
  - 详细设计：新增 `EncryptedMessage.Validate(maxBytes)`；校验必填字段、device_id 格式、size 范围、nonce/ephemeral/ciphertext base64 可解、`kind == encrypted`；失败返回 `400_bad_frame` 或 `413_too_large`。

- [ ] 统一 ACK 中的 server_msg_id
  - 位置：`server/internal/websocket/hub.go:112`、`server/internal/websocket/hub.go:139`
  - 问题：在线直推时 delivered 使用 `client_msg_id` 填 `server_msg_id`，离线入队 ACK 不返回服务端生成 id，客户端状态语义混乱。
  - 详细设计：为在线/离线路径都生成明确 `server_msg_id`；`MsgAck` 对 queued/relayed 均返回 `client_msg_id + server_msg_id`；客户端只用 `client_msg_id` 更新本地发送状态，用 `server_msg_id` 处理离线确认。

- [ ] 限制 WebSocket origin / host 策略
  - 位置：`server/internal/websocket/client.go:27`
  - 问题：`CheckOrigin` 永远 true，文档说明由反向代理控制，但自建部署或本地直连时容易暴露跨站 WS 风险。
  - 详细设计：配置项加入 `ws.allowed_origins`；移动端原生可允许空 Origin；浏览器 Origin 不在 allowlist 时拒绝；README/server-deploy 明确配置方式。

## Storage / Migrations

- [ ] 修复 Postgres `DeleteOffline` 参数绑定
  - 位置：`server/internal/storage/postgres.go:97`
  - 问题：`database/sql` + pgx 对 `ANY($1)` 直接传 `[]string` 可能不按预期编码，存在运行时报错风险。
  - 详细设计：改用 pgx array 支持方式，或构建参数化 `IN ($1,$2,...)`；增加 Postgres 集成测试覆盖批量删除。

- [ ] 统一 SQLite 运行时迁移与 SQL 迁移文件
  - 位置：`server/internal/storage/sqlite.go:34`、`server/migrations/`
  - 问题：SQLite 内置 `migrate` 与迁移文件双轨，后续 schema 容易漂移。
  - 详细设计：引入统一 migration runner；SQLite/Postgres 都执行 `migrations/*.up.sql`；启动时记录 schema version；测试验证全新库和旧库升级。

- [ ] 落实 `offline.max_per_to` 队列上限
  - 位置：`server/internal/config/config.go:40`、`server/internal/websocket/hub.go:136`
  - 问题：配置中有 `MaxPerTo`，但 `EnqueueOffline` 未使用，单设备离线队列可能无限增长。
  - 详细设计：在 store 接口增加按目标设备裁剪或计数；入队前/后保留最新 N 条；超限返回明确 `queue_full` 或丢弃最旧消息并记录指标。

- [ ] 为 file chunk 增加内容存储接口
  - 位置：`server/internal/storage/store.go:36`、`docs/roadmap.md:8`
  - 问题：当前只有分片元数据，没有 `/v1/files/*` endpoint 与内容落盘，文件/图片/语音不可用。
  - 详细设计：新增 `PutFileChunk/GetFileChunk/HasFileChunk`，内容按 `file_id/idx` 存对象目录或 blob store；HTTP PUT/GET 只接受已认证设备；元数据只存密文长度和 sha256。

## Auth / Rate Limit / Ops

- [ ] 为 challenge store 增加容量限制与定期 GC
  - 位置：`server/internal/auth/auth.go:50`
  - 问题：challenge 只在 Issue 时顺手 GC，没有容量上限；恶意请求可制造内存压力。
  - 详细设计：配置 `auth.max_pending_challenges`；Issue 前清理过期并拒绝超限；记录按 IP/device 的失败计数；测试覆盖过期、超限、一次性使用。

- [ ] 实现 ratelimit bucket GC
  - 位置：`server/internal/ratelimit/ratelimit.go:43`
  - 问题：GC 是 TODO，设备量增长后 buckets map 不会收缩。
  - 详细设计：包装 limiter 结构体保存 `lastSeen`；`Allow` 更新访问时间；`GC(idle)` 删除超过 idle 的 bucket。

- [ ] 优化启动错误处理
  - 位置：`server/cmd/nearlink-server/main.go:26`
  - 问题：配置和 logger 初始化失败直接 panic，不利于部署排查。
  - 详细设计：改为 `log.Fatal` 或 stderr 输出后非零退出；配置加载后调用 `Validate()` 检查 duration、driver、dsn、rate limit 等。

- [ ] 增加可观测性基础
  - 位置：`docs/roadmap.md:90`
  - 问题：没有 metrics，无法验证连接数、离线队列长度、转发延迟与限流效果。
  - 详细设计：新增 `/metrics` Prometheus endpoint，指标命名 `nearlink_*`；禁止把 ciphertext、nonce、pubkey 写入标签或日志。

## 测试清单

- [ ] 增加 WebSocket 订阅泄漏/断连清理测试。
- [ ] 增加离线消息 ack 后删除测试，覆盖客户端未 ack 不删除。
- [ ] 增加非法 `msg_send` payload table tests。
- [ ] 增加 SQLite/Postgres storage 行为一致性测试。
- [ ] CI 使用 `go test ./... -race -cover`。
