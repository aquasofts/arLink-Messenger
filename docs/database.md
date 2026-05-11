# NearLink 数据库设计

## 1. 客户端（Room / SQLite）

### 1.1 schema 摘要

| 表 | 主键 | 用途 |
|----|------|------|
| `contacts` | `device_id` | 联系人公钥与信任态 |
| `conversations` | `conv_id` | 会话顶层信息（`peer_device_id` 唯一） |
| `messages` | `id` (= client_msg_id) | 已解密本地消息；INSERT OR IGNORE 实现幂等去重 |
| `keys` | `id` | 本机身份与 root_key 缓存（Keystore 包裹） |
| `outbox` | `client_msg_id` | 待投递的密文，由 Worker 消费 |

### 1.2 关键约束 / 索引

- `messages(conv_id, created_at_ms)` 复合索引，保证按会话分页查询零成本。
- `messages.id` 为 client_msg_id（ULID），全局唯一 → 跨通道天然去重。
- `outbox.next_attempt_at_ms` 单列索引，Worker 用 `WHERE next_attempt_at_ms <= now` 取队首。
- `conversations.peer_device_id` UNIQUE，强制单聊一对一映射。

### 1.3 加密

默认明文 SQLite。SQLCipher 接口已在 `SqlCipherSupport` 预留：
- 用户在设置页启用并输入口令 → 派生 32B passphrase → SupportFactory 注入 → Room 重建。
- passphrase 永不写盘，仅 Keystore 包裹后落 EncryptedFile。
- 口令丢失等价于数据丢失（设计如此，不留后门）。

### 1.4 迁移

首版 v1。后续：
- v2 引入群聊 → `conversations.kind`、`group_members` 表。
- v3 引入消息搜索 FTS4 → `messages_fts` 虚表。

## 2. 服务器（PostgreSQL，开发环境 SQLite）

### 2.1 schema 摘要（详见 `server/migrations/0001_init.up.sql`）

| 表 | 主键 | 用途 |
|----|------|------|
| `devices` | `device_id` | 见过的设备公钥缓存（用于 presence / pull） |
| `offline_queue` | `id` | 在线推不到时缓存 ≤ N 天的密文 |
| `file_chunks` | `(file_id, chunk_idx)` | 大文件分片元数据，仅密文 |
| `auth_challenges` | `challenge_id` | 短期 nonce 缓存（也可放 Redis） |

### 2.2 关键约束

- `offline_queue.to_device_id, ts` 复合索引 → 按收件人按时拉取。
- `file_chunks.file_id` 上有 SHA-256 完整性字段（仅校验，密文级），服务器不解密。
- `auth_challenges.expires_at` 自动清理：cron 或 BRIN 范围分区。

### 2.3 配额 / 清理

| 任务 | 周期 | 说明 |
|------|------|------|
| 离线队列过期 | 1 小时 | TTL 14 天（可配） |
| 文件分片过期 | 1 小时 | TTL 30 天（可配） |
| nonce 过期 | 1 分钟 | TTL 60s |

> 服务器存储设计目标：**没有任何字段含明文消息**。想多看点也看不到。
