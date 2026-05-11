# NearLink Server 部署

> 一句话：装好 Docker → `cd server` → `./deploy.sh` → 完工。
> 老老实实读 1 分钟下面"前置条件"再开干。

---

## 0. 前置条件（必看，1 分钟）

| 你需要 | 是否必须 | 备注 |
|--------|---------|------|
| 一台 Linux/macOS 机器（VPS 或本机皆可） | ✓ | Windows 用户走 WSL2 / Git Bash，见 §6 |
| Docker Engine 24+ | ✓ | `docker --version` |
| Docker Compose v2 | ✓ | `docker compose version` |
| 一个域名 | 公网部署必须 | 否则 Let's Encrypt 不会签发；内网测试可用 `:80` |
| `:80` 与 `:443` 端口空闲 | 公网部署必须 | Caddy 占用这两个端口做 TLS |
| `bash` / `curl` / `openssl` | ✓ | 几乎所有发行版自带 |

> 不需要：手动建库、手动跑 migration、手动准备证书、手动 systemd unit。脚本帮你做。

---

## 1. 一键部署（推荐）

```bash
git clone <your-repo-url> nearlink
cd nearlink/server

cp .env.example .env
# 用 vim/nano 编辑 .env：
#   - NEARLINK_DOMAIN：填你的域名（例如 nearlink.example.com）
#                     若仅本地内网测试，填 :80
#   - NEARLINK_ACME_EMAIL：填你的邮箱，用于 LE 到期提醒
#   - POSTGRES_PASSWORD：留空即可，脚本会自动生成 32 字节随机串

./deploy.sh
```

脚本会做这些事，幂等可重复运行：

1. 检查 `docker` / `docker compose` 是否就绪。
2. 没有 `.env` 就从 `.env.example` 拷一份；引导你填。
3. `POSTGRES_PASSWORD` 为空时**自动**用 `openssl rand -hex 32` 生成并写回 `.env`。
4. 把 `config.template.yaml` 渲染成 `config.yaml`（已有则跳过；要强制重渲染用 `./deploy.sh rebuild`）。
5. `docker compose build server` + `up -d`：拉起 **db + server + caddy** 三个容器。
6. 轮询 `/v1/health` 直到通或超时；最后打印验收信息。

**首次启动**因为要构建镜像 + Caddy 签 LE 证书，约 2–3 分钟。后续启动只要几秒。

---

## 2. 验收

脚本跑完会显示类似：

```
部署完成
- 域名：     nearlink.example.com
- 健康检查： curl https://nearlink.example.com/v1/health
            curl http://127.0.0.1/v1/health
- 日志：     ./deploy.sh logs
- 状态：     ./deploy.sh status

Android 端在 设置 → 服务器地址 中填：
  wss://nearlink.example.com/v1/ws
```

最简单的人工验证：

```bash
curl -fsS https://nearlink.example.com/v1/health
# {"ok":true}
```

如果返回 `{"ok":true}`，**整套部署成功**。可以装 Android App 试加好友、互发消息了。

---

## 3. 日常运维

| 想做什么 | 命令 |
|----------|------|
| 看实时日志 | `./deploy.sh logs` |
| 看容器/健康状态 | `./deploy.sh status` |
| 重启所有服务 | `./deploy.sh restart` |
| 停服（保留数据） | `./deploy.sh down` |
| 拉新代码后强制重建镜像 | `./deploy.sh rebuild` |
| 清空数据卷（删库跑路） | `./deploy.sh nuke` —— 会问你两次确认 |
| 帮助 | `./deploy.sh -h` |

---

## 4. 配置项（`.env`）

| 变量 | 默认 | 说明 |
|------|------|------|
| `NEARLINK_DOMAIN` | — | 必填。公网域名或 `:80` |
| `NEARLINK_ACME_EMAIL` | `admin@example.com` | LE 证书到期通知邮箱 |
| `POSTGRES_PASSWORD` | 自动生成 | 留空即可 |
| `NEARLINK_STORAGE_DRIVER` | `postgres` | `postgres` \| `sqlite`（开发可切 sqlite，免依赖 PG） |
| `NEARLINK_HTTP_ADDR` | `:8080` | server 容器内监听端口 |
| `NEARLINK_LOG_LEVEL` | `info` | `debug` \| `info` \| `warn` \| `error` |
| `NEARLINK_OFFLINE_TTL` | `336h` | 离线消息保留 14 天 |
| `NEARLINK_MAX_MSG_BYTES` | `65536` | 单帧密文上限 |

改完任意一项后跑 `./deploy.sh rebuild`（会重新渲染 `config.yaml` 并重启）。

---

## 5. 仅 SQLite 开发模式（不要 Postgres）

懒得碰 Postgres、就想本地起个 demo？只改 `.env`：

```env
NEARLINK_DOMAIN=:80
NEARLINK_STORAGE_DRIVER=sqlite
```

然后 `./deploy.sh`。注意：仍然会拉起 `db` 容器但 server 不连它（这是 compose 的小缺点，可以手动 `docker compose up -d server caddy` 跳过 db）。

---

## 6. Windows 用户

两种走法：

**A. WSL2（推荐）**
1. 装好 WSL2 + Ubuntu 发行版。
2. 在 Ubuntu 里装 Docker（或直接用 Docker Desktop 把 WSL2 集成开起来）。
3. 在 WSL 里 `cd /mnt/d/git/arLink\ Messenger/server && ./deploy.sh`。

**B. PowerShell + Git Bash**
1. 装 Docker Desktop（自带 WSL2 后端）+ Git for Windows。
2. PowerShell 进 server 目录后运行 `.\deploy.ps1`。它会自动调用 Git Bash 跑 deploy.sh。

---

## 7. 排错速查

| 症状 | 原因 / 处理 |
|------|------------|
| `缺少命令：docker` | 装 Docker Engine 或 Docker Desktop。 |
| `Caddy: making certificate ... timed out` | 域名 DNS 没生效；或 80/443 被防火墙挡。`dig your-domain.com` 看 A 记录。 |
| `pg_isready` 一直失败 | `./deploy.sh logs` 看 db 日志；通常是磁盘满或权限问题。 |
| `.env 缺少 NEARLINK_DOMAIN` | 脚本第一次会自动 cp 模板，重新跑一次并编辑 `.env`。 |
| `health check timeout` | `./deploy.sh logs server` 看应用日志；多半是 DSN 写错（`./deploy.sh rebuild` 会重新渲染）。 |
| 想搬到新机器 | 备份 `pgdata` 卷 + `.env` + `config.yaml` 即可（DB 内容是密文，无安全意义）。 |

---

## 8. 不变量（再强调）

- 服务器**永远不解密任何消息内容**：代码层面没有解密路径；本仓库的 grep 复核命令见 [security.md §10](security.md)。
- 本脚本**不收集**任何遥测；只把日志写到容器 stdout 和 Caddy 文件日志里。
- `.env` 里的密码**不会**被提交到 git（已在 `.gitignore` 中）。

---

## 9. 进阶：横向扩容

单实例够用到几千连接。需要横扩时把 Hub / Presence / RateLimit 切到 Redis；细节见 [roadmap.md §15](roadmap.md)。
