# 测试方案

> 三层金字塔：单元（多）→ 组件（中等）→ 端到端（少而关键）。
> 单元 / 组件测试随代码合入；端到端走人工 + 真机两步走。

## 1. Android 单元测试

运行：

```bash
cd android
./gradlew :app:testDebugUnitTest
```

### 1.1 已覆盖

| 用例 | 类 | 文件 |
|------|----|------|
| 设备 ID 生成 | `IdentityKeyStore` 派生规则 | `DeviceIdTest.kt` |
| 安全码对称 | `SafetyNumber.compute` | `SafetyNumberTest.kt` |
| AEAD 往返 | `AesGcmCipher` | `CryptoEngineTest.kt` |
| 解密 AAD 篡改失败 | 同上 | 同上 |
| 消息去重 | `MessageDao.insertWithDedup` | `MessageDedupTest.kt`（androidTest） |
| 通道选择 | `TransportManager.pickChannel` | `TransportManagerTest.kt` |

### 1.2 Android instrumented 测试

运行（接 USB 真机或 emulator）：

```bash
./gradlew :app:connectedDebugAndroidTest
```

覆盖：

- `MessageDaoTest` —— Room 的 INSERT OR IGNORE 行为。
- `ContactDaoTest` —— Contact CRUD + trust state 升级。

## 2. 服务器单元测试

```bash
cd server
go test ./...
```

### 2.1 已覆盖

| 用例 | 包 | 文件 |
|------|----|------|
| 挑战颁发 + 校验 | `auth` | `auth_test.go` |
| 签名错误拒绝 | `auth` | 同上 |
| Hub 路由在线 vs 离线 | `websocket` | `hub_test.go` |
| Presence 订阅与广播 | `presence` | `presence_test.go` |
| 限流允许 / 拒绝 | `ratelimit` | `ratelimit_test.go` |
| SQLite Store 全 CRUD | `storage` | `sqlite_test.go` |

## 3. 集成 / 端到端测试（手动）

每次大改动至少走一遍：

### T1. 两台设备 BLE 配对
1. A、B 都在 PairScreen。
2. A 列表里看到 B → 点击发起。
3. 双方 SafetyNumberScreen 显示相同 12×5 数字。
4. 都点确认。
5. 验收：双方 ContactsScreen 都出现对方且 trust=VERIFIED。

### T2. 蓝牙发送文本
1. T1 完成。
2. A 向 B 发 "hello"。
3. 验收：B 即时收到；A 端 Message bubble 状态推进 PENDING → SENT → DELIVERED。

### T3. 服务器发送文本
1. 两台都在同一服务器配置。
2. 双方关蓝牙（或拉开距离），保留 WS 连接。
3. A 发消息。
4. 验收：B 收到；状态推进同 T2。

### T4. 离线消息同步
1. B 杀进程。
2. A 发若干条消息（应进入服务器 `offline_queue`）。
3. B 重启 App，等待 5–10 秒。
4. 验收：B 看到 A 发的所有消息，顺序正确，无重复。

### T5. 文件分片传输（v1.1 后启用）
1. A 发 1 张图（>200KB）给 B。
2. 中途切飞行模式 30 秒，恢复。
3. 验收：B 最终拿到完整图片，SHA-256 校验通过。

### T6. 断线重连
1. A 和 B 都连服务器，互发消息正常。
2. 服务器 docker `restart`。
3. 客户端不动，等约 2 分钟。
4. 验收：服务器起来后两台客户端自动重连（指数退避），消息收发恢复，未在线时发出的消息进离线队列。

### T7. 撤回 / 编辑 / 已读
1. A 发 "abc"，2 秒后撤回。
2. 验收：B 端气泡显示"已撤回"。
3. A 发 "world"，长按 → 编辑成 "world!"。
4. 验收：B 端气泡显示 "world!" + "已编辑" 标签。

### T8. 密钥变化告警（手动模拟）
1. 卸载 A 重装（device_id 变）。
2. A 重新走 onboarding 后给 B 发消息。
3. 验收：B 端 ContactsScreen 上 A 标记为 CHANGED（trust 警告）。

## 4. 性能基线（参考）

| 场景 | 期望 |
|------|------|
| 单条文本端到端（蓝牙） | < 150 ms |
| 单条文本端到端（同机房服务器） | < 250 ms |
| 服务器单实例并发连接 | 5,000+（普通 VPS） |
| 服务器单条消息转发 | < 5 ms p50 |
| Android App 启动到聊天列表 | < 1.5 s（中端机） |

## 5. 安全测试（必做）

| 用例 | 期望 |
|------|------|
| 把 ciphertext 中间 1 个 byte 改掉 | 解密失败，UI 不展示，日志 warn |
| 把 from 字段伪造成别人 | 服务器返回 `403_forbidden`，关闭连接 |
| nonce 复用同 key 两次 | 应在客户端测试代码中显式覆盖：相同结果保证不出现 |
| device_id 与 pub 不匹配 | 服务器在 auth 阶段拒绝 |
| WS 帧 > MaxMessageBytes | 服务器返回 `413_too_large` |
| 同设备每秒发 100 条消息 | 大部分被 ratelimit 拒绝 |

## 6. CI 集成（建议）

`.github/workflows/ci.yml`（仓库未附；按需添加）建议：

- Android：`./gradlew :app:testDebugUnitTest :app:lintDebug`
- Go：`go test ./... -race -cover`
- Docker：`docker build -t test server/`

构建产物：debug APK + server 二进制 + 镜像。**不上传** keystore / config.yaml。
