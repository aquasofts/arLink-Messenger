# 安全说明与审计清单

> 目标：让一个独立审计者用半天时间能验证我们的安全声明是否成立。
> 每一条都附"在哪验证"，便于复核与回归。

---

## 1. 威胁模型

| 攻击者 | 能拿到 | 拿不到 |
|--------|--------|--------|
| **服务器运营者**（含内鬼） | 路由元数据：`from`、`to`、`conv_id`、`client_msg_id`、`size`、`ts` | 任何明文消息内容；用户私钥 |
| **网络中间人** | TLS 已防御；只能看到 TLS handshake | 任何 WS 帧内容 |
| **蓝牙近场窃听者** | 看到 BLE 广播的 `eph_id`（每分钟轮换）+ pubkey 前 6 字节；RFCOMM 流是密文 | 明文消息 |
| **目标设备被盗 + 锁屏破解** | Room 数据库内容（默认明文 SQLite） | 启用 SQLCipher 时拿不到；Keystore 内私钥拿不到 |
| **同局域网恶意客户端** | 自己的会话；服务器拒绝伪造 `from` | 别人的消息 |
| **恶意 App 在同手机** | 无 —— App 数据在私有沙盒，且 backup 禁用 | 全部 |

---

## 2. 加密算法与参数

| 用途 | 算法 | 在哪 |
|------|------|------|
| 身份签名 | Ed25519 | `Ed25519X25519.sign` |
| 密钥协商 | X25519 ECDH | `Ed25519X25519.dh` |
| KDF | HKDF-SHA256 | `Hkdf.derive` |
| AEAD（首选） | XChaCha20-Poly1305 (24B nonce, 16B tag) | `XChaChaPolyCipher` |
| AEAD（回退） | AES-256-GCM (12B nonce, 16B tag) | `AesGcmCipher` |
| 设备 ID | base32(sha256(pk))[:24] 小写无 padding | `IdentityKeyStore.deviceIdOf` + `server/internal/auth.DeviceIDFromPubKey` |
| 安全码 | 5200×SHA-512 → 30B → 60 位十进制 → 12 组 ×5 | `SafetyNumber.compute` |

**验证点**：
- `CryptoEngineTest.kt` AEAD 往返 + AAD 篡改失败 + 密文篡改失败。
- `SafetyNumberTest.kt` 对称性 + 1 bit 差异。
- 服务器 `auth_test.go` 校验 `device_id == base32(sha256(pub))[:24]`。

---

## 3. 身份与配对

| 检查项 | 通过条件 | 验证位置 |
|--------|---------|---------|
| 首启动生成 Ed25519 + X25519 | 文件 `identity.pub` 共 64B（两 32B），`identity.sk.enc` 由 EncryptedFile 包裹 | `IdentityKeyStore.initializeIfAbsent` |
| 私钥永不出进程边界 | `dh`/`sign` 在协程内读取并立即 `wipe` | `IdentityKeyStore.sign/dh`、`CryptoUtils.wipe` |
| BT 握手有签名 | `BtHandshake.readHello` 校验 `Ed25519(pkId, "NL-PAIR"||pkX)` + `device_id` 一致 | `BtHandshake.kt` |
| 安全码两端一致 | 字典序排序后拼接 fingerprint，确保 A、B 输出相同 | `SafetyNumber.compute` + `SafetyNumberTest` |
| 公钥变化告警 | 接收方解密失败或公钥 mismatch → 标记 `CHANGED` | `MessageRepository.ingest`（TODO：把失败路径写显式） |

---

## 4. 服务器侧不变量

| 项 | 通过条件 | 验证位置 |
|----|---------|---------|
| 不读密文 | 代码层面 `ciphertext_b64` 仅作 `string` 字段传递，不存在解码路径 | grep `CiphertextB64` 全仓只在 `EncryptedMessage` 与 `storage.Envelope` 出现 |
| 不伪造发件人 | 校验 `frame.from == c.deviceID` | `client.go` dispatch `TypeMsgSend` 分支 |
| 挑战一次性 | `Verify` 成功后 `delete(c.store, p.ChallengeID)` | `auth.go` + `auth_test.go::TestChallenge_OneShot` |
| 限流生效 | 突发后立即拒绝 | `ratelimit_test.go` + `hub_test.go::TestRoute_RateLimited` |
| 帧大小封顶 | `MaxBytes` 在 conn 与 `Route` 双重检查 | `client.go::Upgrader.Handle`、`hub.go::Route` |
| 离线幂等 | `UNIQUE(to_device_id, client_msg_id)` | `migrations/0002_*.sql`、`sqlite_test.go::TestEnqueueDedup` |
| TLS 终止 | Caddy 自动签发 + HSTS | `server/Caddyfile` |
| 日志无密钥 | zap 全局不打印 ciphertext / pub；只能打印 `device_id[:8]` | `logger/logger.go::Short` + grep `ciphertext\|pubkey\|priv\|nonce` |

**人工验证**：
```bash
cd server
grep -RIn -E "(ciphertext_b64|nonce_b64|pubkey|priv|sk_)" --include="*.go" .
# 期望只出现在 frame.go/storage.go 等"传输/存储"字段，不应出现在 log/info 调用里
```

---

## 5. Android 客户端不变量

| 项 | 通过条件 | 验证位置 |
|----|---------|---------|
| 备份禁用 | `android:allowBackup="false"` + `backup_rules.xml` 全 exclude | `AndroidManifest.xml` |
| 明文流量禁用 | `usesCleartextTraffic="false"` | `AndroidManifest.xml` |
| BLE 广播不含 PII | `setIncludeDeviceName(false)`；只发 eph_id + pubkey 前 6B | `BleAdvertiser.kt` |
| 蓝牙权限按版本 | `BLUETOOTH_SCAN`+`neverForLocation`/`ADVERTISE`/`CONNECT` (S+)；`BLUETOOTH`+`FINE_LOCATION` (≤30) | `AndroidManifest.xml` |
| OkHttp 日志默认 NONE | 永不打印密文 | `NetworkModule.kt` |
| 私钥永不进 DB | Keystore + EncryptedFile，不入 Room | `IdentityKeyStore.kt` |
| 关键缓冲 wipe | `mk/ephSk/rootShared/ephShared` 使用完即 `wipe(0)` | `CryptoEngine.kt` |

**人工验证**：
```bash
cd android
grep -RIn -E "Log\.(d|i|v|w|e)" app/src/main | grep -Ei "ciphertext|nonce|secret|priv|key"
# 期望：无任何命中
```

---

## 6. 隐私

| 隐私事项 | 我们的做法 |
|----------|----------|
| 手机号 | 不收集 |
| 通讯录 | 不读取 |
| 位置 | Android 12+ `neverForLocation`；≤11 仅 BLE 扫描所需，我们的代码不消费 |
| 头像/昵称 | 仅本机存储；BLE 广播不含；服务器不见 |
| 分析/埋点 | 无第三方 SDK |
| 崩溃上报 | 默认无；如启用第三方崩溃服务需经用户明确同意，并禁止上传堆栈中的密文 |

---

## 7. 供应链

| 项 | 检查 |
|----|------|
| 依赖版本 | 集中在 `libs.versions.toml` 与 `go.mod`，便于审计 |
| 二进制依赖 | libsodium .so 来自 lazysodium（已知信任来源） |
| 服务器二进制 | 多阶段 Dockerfile + `-trimpath -ldflags="-s -w"` |
| 镜像基础 | `alpine:3.20`（最小化攻击面） |
| 反向代理 | Caddy 官方镜像 `caddy:2-alpine` |
| 数据库 | `postgres:16-alpine` 官方 |

建议：用 `trivy` 周期扫描镜像（CI 中加 `trivy image nearlink/nearlink-server:dev`）。

---

## 8. 操作安全（部署侧）

- 服务器**禁止**与其它服务共用 Postgres（防止越权 SQL 读到 offline_queue）。
- Caddy 日志**不打印** `X-NL-Auth` 头与请求体。
- 服务器主机禁止开放 `:8080` 到公网；只暴露 `:443`。
- 备份只备份"明文意义为零"的部分：Postgres 卷可以备份，但要明白数据库里**也是密文**。
- 私钥/keystore 不能进 git。`.gitignore` 已覆盖 `signing.properties`、`*.keystore`、`*.jks`、`server/config.yaml`。

---

## 9. 已知不足（明文记录，便于审计）

| 项 | 影响 | 缓解 / 路线 |
|----|------|------------|
| 当前是"轻量双棘轮"而非完整 Signal 双棘轮 | root_key 泄露后，**已发送**的消息可被解（前提：攻击者拿到密文 + 已公开的 epk） | Roadmap v2 §5 完整双棘轮 |
| 服务器单实例 in-memory 路由 | 单点故障；横扩需 Redis | Roadmap §15 |
| 没有 Signed PreKey | 首条消息必须双方在线（蓝牙握手） | Roadmap §6 |
| FileChunkWorker 未实现 | 大文件功能不可用 | Roadmap §1 |
| 多设备同身份未支持 | 换机要重新加好友 | Roadmap §8 |

---

## 10. 复核步骤（约半天）

1. 浏览 `docs/architecture.md` + `docs/encryption.md`，建立心智模型。
2. 在仓库根目录跑：
   ```bash
   grep -RIn -E "(ciphertext_b64|nonce_b64|pubkey|priv|sk_)" --include="*.kt" --include="*.go" .
   ```
   逐条确认所有命中均在"协议字段定义"或"存储字段"上下文，**不出现在**日志或网络第三方调用里。
3. 跑测试：`cd android && ./gradlew :app:testDebugUnitTest` 与 `cd server && go test ./...`。
4. 手工执行 `docs/testing.md` 中的 T1–T8。
5. 用两台真机走一遍：A 发消息 → 抓服务器日志 → 确认无明文。
6. 提交报告：每条第 2–4 节中的"检查项"打勾或写下偏离。
