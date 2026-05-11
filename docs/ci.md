# 持续集成与发布（GitHub Actions）

仓库里有 3 条工作流：

| 文件 | 触发时机 | 做什么 |
|------|---------|--------|
| `.github/workflows/android.yml` | push / PR 改 `android/**` | 跑 Android 单元测试 + 构建 Debug APK，作为 artifact 上传（7 天） |
| `.github/workflows/server.yml`  | push / PR 改 `server/**`  | Go 测试 + Linux amd64 二进制；main 或 tag 时构建 **多架构 Docker 镜像**推到 GHCR |
| `.github/workflows/release.yml` | push tag `vX.Y.Z`         | 构建 Release APK + server 多架构二进制 + SHA256SUMS，**发 GitHub Release** |

每条工作流也支持 **workflow_dispatch**，可以在 Actions 页面手动点 Run。

---

## 1. 首次启用

1. 把仓库 push 到 GitHub。
2. 进入 **Settings → Actions → General**：
   - *Actions permissions*：选 "Allow all actions"（或"本仓库 only"）。
   - *Workflow permissions*：选 **Read and write**（`release.yml` 需要写 releases；`server.yml` 需要写 GHCR）。
3. push 到 main 或发个小 PR，看 Actions 页面会自动跑起来。

---

## 2. Android APK 的可选签名

`release.yml` 的 Android 作业会**自动检测**仓库 Secrets，全配齐就走签名流程、否则发 unsigned APK。

想要签名产物，加以下四个 Secrets（`Settings → Secrets and variables → Actions → New repository secret`）：

| Secret | 怎么拿 |
|--------|-------|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release.keystore`（macOS 用 `base64 -i release.keystore`） |
| `ANDROID_KEYSTORE_PASSWORD` | 你创建 keystore 时输入的 store password |
| `ANDROID_KEY_ALIAS` | alias，例如 `nearlink` |
| `ANDROID_KEY_PASSWORD` | key password |

没 keystore？本地一次性生成：

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias nearlink \
  -keyalg RSA -keysize 4096 \
  -validity 36500 \
  -storepass '请换掉我' -keypass '请换掉我' \
  -dname "CN=NearLink, OU=Mobile, O=NearLink, L=-, S=-, C=-"
base64 -w0 release.keystore | xclip -selection clipboard    # 粘到 Secret 里
```

> **丢失 keystore = 这个 applicationId 再也无法发更新**。离线冷备一份。

没配这些 Secret 也没关系——CI 会自动发 `*-release-unsigned.apk`（装不进商店但可以本地 `adb install`）。

---

## 3. Docker 镜像（GHCR）

`server.yml` 的 `docker` 作业在 push 到 main / tag 时推到：

```
ghcr.io/<你的用户名或组织>/<仓库名>/nearlink-server
```

镜像默认**仓库私有**。开放给别人拉的话：

- 进 GitHub 你的 Packages → 点这个 package → *Package settings* → *Change visibility* → Public。
- 或者保持私有，用 PAT 或别的身份登录：
  ```bash
  echo $GH_PAT | docker login ghcr.io -u <user> --password-stdin
  docker pull ghcr.io/<user>/<repo>/nearlink-server:latest
  ```

### 改用 Docker Hub

想推 Docker Hub 就在 `server.yml` 里把登录换成：

```yaml
- name: Log in to Docker Hub
  if: github.event_name == 'push'
  uses: docker/login-action@v3
  with:
    username: ${{ secrets.DOCKERHUB_USERNAME }}
    password: ${{ secrets.DOCKERHUB_TOKEN }}
```

然后把 `env.REGISTRY` 改成 `docker.io`，`env.IMAGE` 改成 `<你的用户名>/nearlink-server`。

---

## 4. 发布一个版本（走完整路径）

```bash
# 确保本地干净，切 main
git checkout main && git pull

# 打 tag 并 push
git tag v0.1.0
git push origin v0.1.0
```

约 10–20 分钟后：

- 你的 Packages 里会出现 `nearlink-server:v0.1.0` + `nearlink-server:0.1` + `nearlink-server:latest`。
- Releases 页面会出现 `v0.1.0`，资产包括：
  ```
  nearlink-v0.1.0.apk
  nearlink-v0.1.0.apk.sha256
  nearlink-server-linux-amd64
  nearlink-server-linux-amd64.sha256
  nearlink-server-linux-arm64
  nearlink-server-linux-arm64.sha256
  SHA256SUMS
  ```

验证：

```bash
# 下载资产到本地后
sha256sum -c SHA256SUMS
```

tag 含 `-rc` / `-beta` / `-alpha` 会自动标记为 **prerelease**。

---

## 5. 常见问题

| 症状 | 处理 |
|------|------|
| `Permission denied to ghcr.io` | 确认仓库 Settings → Actions → *Workflow permissions* 是 **Read and write** |
| Docker 多架构 build 特别慢 | arm64 走的是 QEMU 模拟；要快就加 `runs-on: ubuntu-24.04-arm`（预览期或企业版）或用 matrix 拆两个 job |
| Android 构建 `Could not find gradle-wrapper.jar` | workflow 里已有自动 `gradle wrapper` 生成步骤；只在极个别 checkout 异常会出现。重跑一次 |
| Android `kspDebugKotlin FAILED: KSTypeArgument.type should not have been null` | KSP2 + Hilt 2.51 在 `@Binds` 通配类型上的 bug。`gradle.properties` 里设 `ksp.useKSP2=false`（本仓库默认已关）。后续等 Hilt ≥ 2.53 + KSP2 都成熟再切回。 |
| Release 里 APK 名字不对 | 改 `release.yml` 的 `out="out/nearlink-$tag.apk"` |
| 我只想手动打包，不想每次 push 都跑 | 改 `on:` 只留 `workflow_dispatch`；现成三个 workflow 都支持手动触发 |

---

## 6. 本地复现 CI（可选）

用 [nektos/act](https://github.com/nektos/act)：

```bash
# 跑 android 工作流中的 build job
act -W .github/workflows/android.yml -j build

# 跑 server 测试
act -W .github/workflows/server.yml -j test
```

Docker build job 在 act 里不好跑（需要 buildx 驱动），推荐在 GitHub 上直接触发验证。
