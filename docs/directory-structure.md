# 完整目录结构

```
arLink Messenger/
├── README.md
├── docs/
│   ├── architecture.md           # 第 1 部分：总体架构
│   ├── directory-structure.md    # 本文：完整目录树
│   ├── protocol.md               # WebSocket 协议
│   ├── encryption.md             # 端到端加密协议
│   ├── bluetooth.md              # 蓝牙发现 + 传输协议
│   ├── database.md               # 数据库设计
│   ├── android-build.md          # Android 编译/运行
│   ├── server-deploy.md          # 服务器部署
│   ├── security.md               # 安全说明与威胁模型
│   ├── testing.md                # 测试方案
│   └── roadmap.md                # 后续路线
│
├── android/
│   ├── build.gradle.kts          # Root Gradle (Kotlin DSL)
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── gradle/
│   │   └── libs.versions.toml    # 版本目录
│   ├── .gitignore
│   └── app/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml
│           │   ├── kotlin/com/nearlink/messenger/
│           │   │   ├── NearLinkApp.kt
│           │   │   ├── MainActivity.kt
│           │   │   │
│           │   │   ├── core/
│           │   │   │   ├── crypto/
│           │   │   │   │   ├── CryptoEngine.kt
│           │   │   │   │   ├── IdentityKeyStore.kt
│           │   │   │   │   ├── SessionKeyDeriver.kt
│           │   │   │   │   ├── SafetyNumber.kt
│           │   │   │   │   └── AeadCipher.kt
│           │   │   │   │
│           │   │   │   ├── bluetooth/
│           │   │   │   │   ├── BluetoothEngine.kt
│           │   │   │   │   ├── BleAdvertiser.kt
│           │   │   │   │   ├── BleScanner.kt
│           │   │   │   │   ├── RfcommServer.kt
│           │   │   │   │   ├── RfcommClient.kt
│           │   │   │   │   ├── BtFraming.kt          # 长度前缀分帧
│           │   │   │   │   └── BtHandshake.kt        # 公钥交换 + 安全码
│           │   │   │   │
│           │   │   │   ├── network/
│           │   │   │   │   ├── WebSocketEngine.kt
│           │   │   │   │   ├── WsAuthenticator.kt
│           │   │   │   │   ├── WsHeartbeat.kt
│           │   │   │   │   └── WsMessageCodec.kt
│           │   │   │   │
│           │   │   │   ├── transport/
│           │   │   │   │   ├── Transport.kt          # 接口
│           │   │   │   │   ├── TransportManager.kt   # 通道选择
│           │   │   │   │   ├── Envelope.kt
│           │   │   │   │   └── DeliveryAck.kt
│           │   │   │   │
│           │   │   │   ├── database/
│           │   │   │   │   ├── NearLinkDatabase.kt
│           │   │   │   │   ├── DatabaseModule.kt
│           │   │   │   │   └── SqlCipherSupport.kt   # 预留接口
│           │   │   │   │
│           │   │   │   ├── model/
│           │   │   │   │   ├── Contact.kt
│           │   │   │   │   ├── Conversation.kt
│           │   │   │   │   ├── Message.kt
│           │   │   │   │   ├── MessageStatus.kt
│           │   │   │   │   ├── MessageType.kt
│           │   │   │   │   └── PresenceState.kt
│           │   │   │   │
│           │   │   │   ├── protocol/
│           │   │   │   │   ├── WireMessage.kt
│           │   │   │   │   ├── BtPacket.kt
│           │   │   │   │   └── Json.kt               # kotlinx.serialization 配置
│           │   │   │   │
│           │   │   │   ├── permissions/
│           │   │   │   │   ├── PermissionHelper.kt
│           │   │   │   │   └── BluetoothPermissions.kt
│           │   │   │   │
│           │   │   │   ├── audio/
│           │   │   │   │   ├── AudioRecorder.kt
│           │   │   │   │   └── AudioPlayer.kt
│           │   │   │   │
│           │   │   │   └── file/
│           │   │   │       ├── FileChunker.kt
│           │   │   │       ├── FileHashVerifier.kt
│           │   │   │       └── AttachmentStore.kt
│           │   │   │
│           │   │   ├── data/
│           │   │   │   ├── local/
│           │   │   │   │   ├── dao/
│           │   │   │   │   │   ├── ContactDao.kt
│           │   │   │   │   │   ├── ConversationDao.kt
│           │   │   │   │   │   ├── MessageDao.kt
│           │   │   │   │   │   ├── KeyDao.kt
│           │   │   │   │   │   └── OutboxDao.kt
│           │   │   │   │   ├── entity/
│           │   │   │   │   │   ├── ContactEntity.kt
│           │   │   │   │   │   ├── ConversationEntity.kt
│           │   │   │   │   │   ├── MessageEntity.kt
│           │   │   │   │   │   ├── KeyEntity.kt
│           │   │   │   │   │   └── OutboxEntity.kt
│           │   │   │   │   └── prefs/
│           │   │   │   │       └── SettingsStore.kt
│           │   │   │   │
│           │   │   │   └── repository/
│           │   │   │       ├── IdentityRepository.kt
│           │   │   │       ├── ContactRepository.kt
│           │   │   │       ├── ConversationRepository.kt
│           │   │   │       ├── MessageRepository.kt
│           │   │   │       └── SettingsRepository.kt
│           │   │   │
│           │   │   ├── domain/
│           │   │   │   └── usecase/
│           │   │   │       ├── BootstrapIdentityUseCase.kt
│           │   │   │       ├── DiscoverPeersUseCase.kt
│           │   │   │       ├── PairContactUseCase.kt
│           │   │   │       ├── ConfirmSafetyNumberUseCase.kt
│           │   │   │       ├── SendMessageUseCase.kt
│           │   │   │       ├── ReceiveMessageUseCase.kt
│           │   │   │       ├── RevokeMessageUseCase.kt
│           │   │   │       ├── EditMessageUseCase.kt
│           │   │   │       ├── MarkReadUseCase.kt
│           │   │   │       ├── ObserveContactsUseCase.kt
│           │   │   │       └── ObserveMessagesUseCase.kt
│           │   │   │
│           │   │   ├── ui/
│           │   │   │   ├── navigation/
│           │   │   │   │   └── NearLinkNavGraph.kt
│           │   │   │   ├── theme/
│           │   │   │   │   ├── Theme.kt
│           │   │   │   │   ├── Color.kt
│           │   │   │   │   └── Type.kt
│           │   │   │   ├── components/
│           │   │   │   │   ├── MessageBubble.kt
│           │   │   │   │   ├── ContactRow.kt
│           │   │   │   │   ├── PresenceDot.kt
│           │   │   │   │   ├── SafetyNumberView.kt
│           │   │   │   │   └── PermissionGate.kt
│           │   │   │   └── screens/
│           │   │   │       ├── onboarding/OnboardingScreen.kt
│           │   │   │       ├── permission/PermissionScreen.kt
│           │   │   │       ├── home/HomeScreen.kt
│           │   │   │       ├── home/HomeViewModel.kt
│           │   │   │       ├── contacts/ContactsScreen.kt
│           │   │   │       ├── contacts/ContactsViewModel.kt
│           │   │   │       ├── pair/PairScreen.kt
│           │   │   │       ├── pair/PairViewModel.kt
│           │   │   │       ├── pair/SafetyNumberScreen.kt
│           │   │   │       ├── chat/ChatScreen.kt
│           │   │   │       ├── chat/ChatViewModel.kt
│           │   │   │       ├── profile/ProfileScreen.kt
│           │   │   │       └── settings/SettingsScreen.kt
│           │   │   │
│           │   │   ├── service/
│           │   │   │   ├── NearLinkForegroundService.kt
│           │   │   │   ├── BluetoothScanService.kt
│           │   │   │   └── WsConnectionService.kt
│           │   │   │
│           │   │   ├── worker/
│           │   │   │   ├── MessageRetryWorker.kt
│           │   │   │   ├── OfflinePullWorker.kt
│           │   │   │   └── FileChunkWorker.kt
│           │   │   │
│           │   │   └── di/
│           │   │       ├── CryptoModule.kt
│           │   │       ├── BluetoothModule.kt
│           │   │       ├── NetworkModule.kt
│           │   │       ├── TransportModule.kt
│           │   │       ├── RepositoryModule.kt
│           │   │       └── UseCaseModule.kt
│           │   │
│           │   └── res/
│           │       ├── values/
│           │       │   ├── strings.xml
│           │       │   ├── colors.xml
│           │       │   └── themes.xml
│           │       ├── values-zh-rCN/
│           │       │   └── strings.xml
│           │       ├── drawable/
│           │       └── mipmap-anydpi-v26/
│           │
│           ├── test/kotlin/com/nearlink/messenger/
│           │   ├── crypto/CryptoEngineTest.kt
│           │   ├── crypto/SafetyNumberTest.kt
│           │   ├── transport/TransportManagerTest.kt
│           │   ├── model/MessageDedupTest.kt
│           │   └── core/DeviceIdTest.kt
│           │
│           └── androidTest/kotlin/com/nearlink/messenger/
│               ├── db/MessageDaoTest.kt
│               └── db/ContactDaoTest.kt
│
└── server/
    ├── go.mod
    ├── go.sum
    ├── Dockerfile
    ├── docker-compose.yml         # postgres + server (+ caddy 可选)
    ├── Caddyfile                  # 反向代理示例
    ├── config.example.yaml
    ├── README.md
    ├── Makefile
    ├── cmd/
    │   └── nearlink-server/
    │       └── main.go
    ├── internal/
    │   ├── config/
    │   │   └── config.go
    │   ├── logger/
    │   │   └── logger.go
    │   ├── auth/
    │   │   ├── auth.go            # Ed25519 挑战签名认证
    │   │   ├── challenge.go
    │   │   └── auth_test.go
    │   ├── websocket/
    │   │   ├── hub.go
    │   │   ├── client.go
    │   │   ├── upgrader.go
    │   │   └── hub_test.go
    │   ├── message/
    │   │   ├── envelope.go
    │   │   ├── router.go
    │   │   ├── offline_queue.go
    │   │   └── router_test.go
    │   ├── presence/
    │   │   ├── presence.go
    │   │   └── presence_test.go
    │   ├── storage/
    │   │   ├── store.go           # 接口
    │   │   ├── postgres.go
    │   │   ├── sqlite.go          # 开发环境
    │   │   └── files.go           # 大文件分片元数据
    │   └── ratelimit/
    │       ├── ratelimit.go
    │       └── ratelimit_test.go
    └── migrations/
        ├── 0001_init.up.sql
        ├── 0001_init.down.sql
        ├── 0002_offline_queue.up.sql
        └── 0002_offline_queue.down.sql
```

## 文件 / 模块汇总

| 类别 | 数量级 |
|------|--------|
| Android Kotlin 文件 | ~85 |
| Go 文件 | ~25 |
| SQL 迁移 | 4 |
| 文档 | 11 |
| Gradle/构建 | 6 |

> 注：本工程目录名沿用用户填写的 `arLink Messenger`（路径首字母小写）。包名统一用 `com.nearlink.messenger`。
