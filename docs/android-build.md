# Android 客户端编译教程（Windows / macOS / Linux）

> 路线图：装环境 → 让 Gradle Wrapper 就位 → 配置服务器地址 → 编 Debug APK → 装到真机。
> 全程不需要写代码。每一步都给出**期望产物**与**常见错误**。

---

## 0. 一定要看的事

- 蓝牙功能**强烈建议真机**调试。Android Studio 的模拟器没有真实 BLE。
- 仓库已提交 Gradle Wrapper（`gradlew` / `gradlew.bat` / `gradle-wrapper.jar`）。第一次编译只需要联网下载 Gradle 8.9 发行包与 Android 依赖。
- 包名是 `com.nearlink.messenger`，debug 变体会自动加 `.debug` 后缀（两个 build 可以共存）。

---

## 1. 装环境（10 分钟）

### Windows

| 软件 | 推荐版本 | 下载 |
|------|---------|------|
| **Android Studio** | Hedgehog (2023.1) 或更新 | https://developer.android.com/studio |
| **JDK 17** | Temurin/Microsoft 17 | Android Studio 自带 jbr-17，可直接用 |
| **Git for Windows** | 最新 | https://git-scm.com/ |

> 装 Android Studio 时勾选默认组件即可（Android SDK + Platform-Tools 会自动装）。
> 如果你已经有别的 SDK 路径，确保 `Tools → SDK Manager` 里至少装了 **Android 14 (API 34) 平台**与 **Android SDK Build-Tools 34.x**。

### macOS / Linux

```bash
# macOS (Homebrew)
brew install --cask android-studio
brew install --cask temurin@17

# Ubuntu (snap)
sudo snap install android-studio --classic
sudo apt install openjdk-17-jdk
```

---

## 2. 拿到代码

```bash
git clone <你的仓库 URL> nearlink
cd nearlink
```

期望产物：

```
nearlink/
├── android/        ← 这次只关心这个目录
├── server/
└── docs/
```

---

## 3. 验证 Gradle Wrapper

仓库已经自带 wrapper；第一次执行会自动下载 Gradle 8.9。

### Android Studio

1. 打开 **Android Studio**，选 `File → Open`，定位到 `nearlink/android/` 目录（注意是 `android` 子目录，不是仓库根）。
2. 弹窗 "Trust Project" 选信任。
3. 右下角会自动出现 "Gradle Sync" 进度条；第一次会下载 Gradle 8.9 + 依赖（约 200–500MB）。
4. **等到状态栏显示 `Gradle sync finished`**，并且 Project 视图里看到 `app` 模块（带绿色 Android 图标）即成功。

期望产物：

```
android/gradle/wrapper/gradle-wrapper.jar
android/gradlew                              ← Linux/macOS 启动器
android/gradlew.bat                          ← Windows 启动器
```

### 命令行

```bash
cd android
./gradlew --version          # macOS / Linux / WSL / Git Bash
gradlew.bat --version        # Windows PowerShell / CMD
```

输出里应看到 `Gradle 8.9`。

---

## 4. 配置 SDK 路径（仅命令行用户）

Android Studio 的用户可以**跳过**这一节——它会自动写好。

命令行用户在 `android/local.properties` 里写：

```properties
# Windows
sdk.dir=C\:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk

# macOS
sdk.dir=/Users/<你>/Library/Android/sdk

# Linux
sdk.dir=/home/<你>/Android/Sdk
```

---

## 5. 编译 Debug APK

### Android Studio 一键

1. 顶部 Configurations 选 `app`。
2. 按 **Build → Build Bundle(s) / APK(s) → Build APK(s)**。
3. 完成后右下角点 **locate**，可看到 `app-debug.apk`。

### 命令行

```bash
cd android
./gradlew :app:assembleDebug          # macOS / Linux / WSL / Git Bash
gradlew.bat :app:assembleDebug        # Windows PowerShell / CMD
```

期望产物：

```
android/app/build/outputs/apk/debug/app-debug.apk
```

第一次编译大概 3–8 分钟；之后增量编译几十秒。

---

## 6. 配置服务器地址（两种方式）

服务器地址决定 App 启动后自动连哪台中转服务器。蓝牙功能不依赖它，但你想做"远距离收发"测试就需要。

### 方式 1：编译期注入（推荐——免去运行时再点设置）

```bash
# 公网部署：
./gradlew :app:assembleDebug -Pnearlink.defaultServer=wss://your-domain.com/v1/ws

# 本机测试（Android 模拟器访问宿主机用 10.0.2.2）：
./gradlew :app:assembleDebug -Pnearlink.defaultServer=ws://10.0.2.2:8080/v1/ws
```

它会写到 `BuildConfig.DEFAULT_SERVER_URL`。
注意：当前 `SettingsScreen` 默认显示**用户已保存的地址**；编译期默认值仅用于"用户从未填过"的情况，要 100% 生效请用方式 2。

### 方式 2：运行时填

启动 App → `设置` → `服务器地址`：

| 场景 | 填什么 |
|------|--------|
| 你跑了 `server/deploy.sh`，公网域名 | `wss://your-domain.com/v1/ws` |
| 内网 / 本机部署，无 TLS | `ws://<服务器局域网 IP>:80/v1/ws` |
| 用 Android 模拟器测同机 server | `ws://10.0.2.2:80/v1/ws` |

留空表示**只用蓝牙**，不连服务器（也是合法用法）。

---

## 7. 安装到真机

1. 真机：`设置 → 关于手机`，连点 7 次"版本号"，进入开发者模式。
2. `设置 → 系统 → 开发者选项`，打开 **USB 调试**。
3. 用数据线连电脑（不是充电线），手机上同意"允许 USB 调试"指纹。

```bash
# 检查设备已连
adb devices
# List of devices attached
# RFCN12345678   device

# 安装
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

期望：手机桌面出现 **NearLink** 图标。

---

## 8. 第一次启动应做的事

1. 点开 App → 自动跳 Onboarding，等 1–2 秒后显示 `device_id` 前缀，按 **开始使用**。
2. 进入权限页：授予 **附近设备 / 蓝牙 / 通知**（Android 12+ 必须）。
3. 进首页 → 右下 `+` → **Pair**。
4. 想测蓝牙？两台手机重复 1–3，互相点对方的列表项 → 看到 12 组 ×5 位安全码两边一致 → 都按"确认"。
5. 想测服务器？进 `设置` 填好 `wss://` 地址，重启 App，状态栏会显示前台服务通知"已连接·端到端加密"。

---

## 9. 看日志（调试用）

```bash
# 全部 NearLink 相关日志
adb logcat -s NearLinkApp:V WebSocketEngine:V BluetoothEngine:V CryptoEngine:V

# 只看错误
adb logcat *:E
```

App 的日志**绝不会**打印密文、私钥、AEAD nonce。如果你看到这些字段被打出来，那是 bug，请提 issue。

---

## 10. 常见报错速查

| 报错关键字 | 原因 | 处理 |
|-----------|------|------|
| `Could not find gradle-wrapper.jar` | 没跑过 §3 | 去 §3 用 Android Studio 同步一次，或 `gradle wrapper --gradle-version 8.9` |
| `SDK location not found` | `local.properties` 没写 sdk.dir | 见 §4，或用 Android Studio 打开一次让它自动写 |
| `Could not resolve androidx.security:security-crypto:...` | Maven 没拉到 | 检查网络；国内用户可在 `settings.gradle.kts` 加阿里云镜像 |
| `Compose Compiler ...incompatible Kotlin` | AGP/Kotlin/Compose 不匹配 | 别动 `libs.versions.toml` 里的版本号；Studio 提示升级请拒绝 |
| `Process 'command 'javac''` 报错 / `invalid target release: 17` | JDK 不是 17 | Studio: `File → Settings → Build, Execution → Gradle → Gradle JDK` 选 17；命令行 `JAVA_HOME` 指向 17 |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | 真机空间不够 | 清缓存或换设备 |
| `INSTALL_FAILED_USER_RESTRICTED` (小米/华为) | OEM 限制 USB 安装 | 在开发者选项打开"通过 USB 安装"或"USB 调试（安全设置）" |
| 启动闪退 `UnsatisfiedLinkError: sodium` | 某些 ABI 缺 .so | 在 `CryptoModule` 把 `@Binds` 改成绑定 `AesGcmCipher` 即可（详见 [security.md §2](../docs/security.md)） |
| Android 12 设备扫不到附近设备 | 蓝牙权限没给 | 进 App 信息 → 权限 → 全部允许；重启 App |
| WS 一直 `state=CONNECTING` 后退避 | 服务器 URL 错或证书没就绪 | `curl -v wss://your-domain.com/v1/ws` 看能不能通；服务器侧 `./deploy.sh status` |

---

## 11. 想发 Release 包？

1. 用 Android Studio `Build → Generate Signed Bundle / APK`，选 APK，新建 keystore（**保存好密码！丢了等于丢身份**）。
2. 或者命令行：在 `android/signing.properties` 里填：

   ```properties
   storeFile=/abs/path/to/release.keystore
   storePassword=********
   keyAlias=nearlink
   keyPassword=********
   ```

   然后：

   ```bash
   ./gradlew :app:assembleRelease
   # 产物：android/app/build/outputs/apk/release/app-release.apk
   ```

`signing.properties` 与 `*.keystore` 已在 `.gitignore`，**不要**提交。

---

## 12. 卸载会丢什么

| 删除哪部分 | 影响 |
|-----------|------|
| 卸载 App | 全部丢失：身份私钥、聊天记录、附件、联系人 |
| 清除存储 | 同上 |
| 关掉前台服务通知 | 系统会更激进地杀进程；建议保持 |

身份本质是设备本地长期密钥，**不能找回**——这就是去账号化的代价。下个版本会做"链式签发子设备"做迁移，见 [roadmap §8](../docs/roadmap.md)。

---

## 13. 下一步

跑通编译 + 安装后：
- 走一遍 [docs/testing.md](../docs/testing.md) 的 T1–T4 端到端用例。
- 想看 App 怎么连服务器：先 `cd ../server && ./deploy.sh`。
- 想改 UI/协议：去 [docs/architecture.md](../docs/architecture.md) 找模块入口。
