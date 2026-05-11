# NearLink Messenger

> 去账号、端到端加密的即时通讯。蓝牙近场直传 + 自建服务器密文中转，双通道任一可用即送达。

<!-- 把 OWNER/REPO 替换成你的 GitHub `用户名/仓库名` -->
[![android](https://github.com/OWNER/REPO/actions/workflows/android.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/android.yml)
[![server](https://github.com/OWNER/REPO/actions/workflows/server.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/server.yml)
[![release](https://github.com/OWNER/REPO/actions/workflows/release.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/release.yml)

| 端 | 技术栈 | 状态 |
|----|--------|------|
| Android | Kotlin · Jetpack Compose · Hilt · Room · OkHttp WS · BLE + RFCOMM · Tink + libsodium | 可编译运行 |
| Server  | Go 1.22 · gorilla/websocket · PostgreSQL/SQLite · zap · Docker Compose · Caddy | 可一键部署 |
| CI/CD   | GitHub Actions：每次 push 自动测试 + 出 APK / Docker；tag 出 Release | 见 [docs/ci.md](docs/ci.md) |

---

## 1. 它在解决什么问题

| 痛点 | 我们的回答 |
|------|-----------|
| 注册要手机号 | 不要。设备首次启动当场生成 Ed25519 身份。 |
| 服务器看得到我的消息 | 看不到。服务器**只见密文**，没有任何解密路径。 |
| 没网就废了 | 蓝牙近场直接互发，不依赖任何网络。 |
| 跨房间就废了 | 同一台自建服务器中转密文，离线还能离线队列。 |
| 配对靠扫码不靠谱 | 双方面对面核对 12 组 ×5 位安全码。 |

---

## 2. 三分钟试用

### 启动服务器

```bash
cd server
cp config.example.yaml config.yaml          # 自行编辑（开发可不动）
go run ./cmd/nearlink-server -c config.yaml # 监听 :8080
```

健康检查：

```bash
curl http://127.0.0.1:8080/v1/health
# {"ok":true}
```

### 编译 Android

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次启动：自动生成本机身份 → 申请蓝牙/通知权限 → 进入聊天列表。
在 **设置 → 服务器地址** 填写你的服务器 URL（开发 `ws://10.0.2.2:8080/v1/ws`；生产 `wss://your-domain.com`）。

### 走一遍 happy path

1. 两台手机都装好 App，完成 onboarding。
2. 都进入 **Pair**（首页 + 按钮）。
3. 互相看到对方设备，点一台发起握手。
4. 双方屏幕显示**完全相同**的安全码（12 组 ×5 位）→ 都点"确认"。
5. 蓝牙直接发消息 ✓。把两台都连到同一服务器后，远距离也能收发 ✓。
6. 关闭蓝牙，再发；走服务器路径 ✓。两边都不在线时入离线队列，回来自动同步 ✓。

---

## 3. 仓库布局

```
arLink Messenger/
├── README.md                # 本文
├── android/                 # Android 客户端（Kotlin DSL Gradle）
│   ├── app/src/main/kotlin/com/nearlink/messenger/
│   │   ├── core/{crypto,bluetooth,network,transport,database,model,protocol,permissions,audio,file}
│   │   ├── data/{local,repository}
│   │   ├── domain/usecase
│   │   ├── ui/{navigation,theme,components,screens}
│   │   ├── service · worker · di
│   │   └── NearLinkApp.kt · MainActivity.kt
│   └── gradle/libs.versions.toml
├── server/                  # Go 服务器
│   ├── cmd/nearlink-server/main.go
│   ├── internal/{config,logger,auth,websocket,presence,ratelimit,storage,message}
│   ├── migrations/
│   ├── Dockerfile · docker-compose.yml · Caddyfile
│   └── config.example.yaml
└── docs/                    # 11 份文档
    ├── architecture.md
    ├── directory-structure.md
    ├── protocol.md
    ├── encryption.md
    ├── bluetooth.md
    ├── database.md
    ├── android-build.md
    ├── server-deploy.md
    ├── security.md
    ├── testing.md
    └── roadmap.md
```

---

## 4. 文档导航（按职责而非顺序）

| 我想… | 看这份 |
|------|--------|
| 了解全局架构 | [architecture.md](docs/architecture.md) |
| 实现 / 改协议 | [protocol.md](docs/protocol.md) |
| 审计加密设计 | [encryption.md](docs/encryption.md) · [security.md](docs/security.md) |
| 调试 BLE / RFCOMM | [bluetooth.md](docs/bluetooth.md) |
| 看数据模型 | [database.md](docs/database.md) · [directory-structure.md](docs/directory-structure.md) |
| 编 / 跑客户端 | [android-build.md](docs/android-build.md) |
| 部署服务器 | [server-deploy.md](docs/server-deploy.md) |
| 跑测试 | [testing.md](docs/testing.md) |
| 配 CI / 自动出 APK / Docker / Release | [ci.md](docs/ci.md) |
| 看后续计划 | [roadmap.md](docs/roadmap.md) |

---

## 5. 验收清单（对照需求）

| # | 需求 | 状态 |
|---|------|------|
| 1 | Android 可编译安装 | ✓ |
| 2 | 首启生成本机身份 | ✓ (`OnboardingViewModel.bootstrap`) |
| 3 | 显示 device_id 与指纹 | ✓ (`ProfileScreen`) |
| 4 | 蓝牙发现附近设备 | ✓ (`BleScanner` + `PairScreen`) |
| 5 | 蓝牙交换身份 | ✓ (`BtHandshake`) |
| 6 | 显示并确认安全码 | ✓ (`SafetyNumber` + `SafetyNumberScreen`) |
| 7 | 保存联系人 | ✓ (`ContactRepository`) |
| 8 | 打开聊天窗口 | ✓ (`ChatScreen`) |
| 9 | 端到端加密文本 | ✓ (`CryptoEngine.seal/openWithPeer`) |
| 10 | 蓝牙发送消息 | ✓ (`BluetoothEngine.send`) |
| 11 | 配置服务器地址 | ✓ (`SettingsScreen`) |
| 12 | 自动连接服务器 | ✓ (`SettingsRepository.connectIfConfigured`) |
| 13 | WS 认证 | ✓ (`WsAuthenticator` + 服务器 `auth.Challenger`) |
| 14 | 服务器中转密文 | ✓ (`Hub.Route`) |
| 15 | 服务器不可解密 | ✓ (设计上无解密路径，见 security.md) |
| 16 | BT / Server / Offline 状态 | ✓ (`PresenceState.aggregate`) |
| 17 | 保存聊天记录 | ✓ (Room `messages` 表) |
| 18 | 图片/文件/语音骨架 | ✓ (`FileChunker/AttachmentStore/AudioRecorder` + `FileChunkWorker` TODO) |
| 19 | 断线重连 + 重试 | ✓ (`WebSocketEngine.connectLoop` + `MessageRetryWorker`) |
| 20 | 部署 / 使用文档 | ✓ (本 README + 10 份 docs/) |

---

## 6. 已知局限

- **AEAD/握手**走的是 "轻量双棘轮"（每条消息一次性 ephemeral X25519）。完整 Signal 双棘轮（含 chain key 推进 + DH ratchet）已列入 [roadmap.md](docs/roadmap.md) v2。
- **FileChunkWorker** 是骨架；真正的 resumable 上下传（含 HTTPS `/v1/files/*` 服务端 endpoint）排进 v1.1。
- **多设备同账号** 暂不支持（一身份 = 一设备）。v2 计划用"链式签发子设备公钥"实现。
- **群聊** 暂不支持，schema 已为 `conv_id` 与 `device_id` 解耦做好预留。

---

## 7. License

建议 GPLv3 或 MPL 2.0（取决于是否计划闭源 Fork）。仓库根目录尚未放 `LICENSE` 文件，请按需添加。
