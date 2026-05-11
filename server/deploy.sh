#!/usr/bin/env bash
# NearLink Server 一键部署脚本
#
# 用法：
#   ./deploy.sh             # 首次部署 / 更新（默认）
#   ./deploy.sh up          # = 默认
#   ./deploy.sh down        # 停服务（保留卷与数据）
#   ./deploy.sh restart     # 重启
#   ./deploy.sh logs        # 跟随日志
#   ./deploy.sh status      # 看容器状态 + 健康检查
#   ./deploy.sh rebuild     # 强制重新 build server 镜像
#   ./deploy.sh nuke        # 删除全部数据卷（**不可恢复**，会问你两次）
#
# 设计原则：
#   - 幂等：能反复运行不出错。
#   - 不静默覆盖现有 .env / config.yaml；如发现存在会跳过并提示。
#   - 不把任何密码/密钥打印到屏幕；只显示掩码（前 4 位 + ****）。

set -Eeuo pipefail

# --- 路径 ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE=".env"
ENV_EXAMPLE=".env.example"
CONFIG_FILE="config.yaml"
CONFIG_TEMPLATE="config.template.yaml"

# --- 颜色 ---
if [[ -t 1 ]]; then
    RED=$'\033[31m'; YEL=$'\033[33m'; GRN=$'\033[32m'; BLU=$'\033[34m'; DIM=$'\033[2m'; RST=$'\033[0m'
else
    RED=""; YEL=""; GRN=""; BLU=""; DIM=""; RST=""
fi

step()  { echo "${BLU}==>${RST} $*"; }
ok()    { echo "${GRN}✓${RST} $*"; }
warn()  { echo "${YEL}!${RST} $*"; }
die()   { echo "${RED}✗ $*${RST}" >&2; exit 1; }

mask() {
    # mask "verysecret" -> "very****"
    local s="$1"
    if [[ ${#s} -le 4 ]]; then echo "****"; else echo "${s:0:4}****"; fi
}

# --- 依赖检查 ---
need() {
    command -v "$1" >/dev/null 2>&1 || die "缺少命令：$1（请先安装）"
}

check_prereqs() {
    step "检查依赖"
    need docker
    # docker compose v2 是 docker 的子命令；v1 是 docker-compose。两者择一。
    if docker compose version >/dev/null 2>&1; then
        COMPOSE=(docker compose)
    elif command -v docker-compose >/dev/null 2>&1; then
        COMPOSE=(docker-compose)
        warn "检测到旧版 docker-compose（v1）。建议升级到 Docker Compose v2。"
    else
        die "缺少 docker compose。请安装 Docker Engine 24+ 与 Compose Plugin。"
    fi
    ok "docker $(docker --version | awk '{print $3}' | tr -d ',')"
    ok "compose $("${COMPOSE[@]}" version --short 2>/dev/null || echo unknown)"
}

# --- .env 生成 / 加载 ---
ensure_env() {
    step "处理 .env"
    if [[ ! -f "$ENV_FILE" ]]; then
        [[ -f "$ENV_EXAMPLE" ]] || die "缺少 $ENV_EXAMPLE，无法生成 .env"
        cp "$ENV_EXAMPLE" "$ENV_FILE"
        ok "已根据 $ENV_EXAMPLE 创建 $ENV_FILE"
        warn "请编辑 $ENV_FILE：至少填写 NEARLINK_DOMAIN 与 NEARLINK_ACME_EMAIL，然后重新运行本脚本。"
        # 公网部署时希望用户主动确认域名；本地内网（:80）则可继续。
        local domain_in_example
        domain_in_example=$(grep -E '^NEARLINK_DOMAIN=' "$ENV_FILE" | head -n1 | cut -d= -f2-)
        if [[ "$domain_in_example" == "nearlink.example.com" ]]; then
            exit 0
        fi
    fi

    # 加载 .env（仅 KEY=VAL 行）
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a

    # 必填项
    [[ -n "${NEARLINK_DOMAIN:-}" ]] || die ".env 缺少 NEARLINK_DOMAIN"

    # POSTGRES_PASSWORD 为空则随机生成并写回 .env
    if [[ -z "${POSTGRES_PASSWORD:-}" ]]; then
        if command -v openssl >/dev/null 2>&1; then
            POSTGRES_PASSWORD="$(openssl rand -hex 32)"
        else
            # 退而求其次
            POSTGRES_PASSWORD="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom 2>/dev/null | head -c 48 || true)"
            [[ -n "$POSTGRES_PASSWORD" ]] || die "无法生成随机密码：缺 openssl 与 /dev/urandom"
        fi
        # 写回（兼容 GNU/BSD sed）
        if sed --version >/dev/null 2>&1; then
            sed -i -E "s|^POSTGRES_PASSWORD=.*$|POSTGRES_PASSWORD=${POSTGRES_PASSWORD}|" "$ENV_FILE"
        else
            sed -i '' -E "s|^POSTGRES_PASSWORD=.*$|POSTGRES_PASSWORD=${POSTGRES_PASSWORD}|" "$ENV_FILE"
        fi
        ok "已生成 POSTGRES_PASSWORD=$(mask "$POSTGRES_PASSWORD") 并写回 $ENV_FILE"
        export POSTGRES_PASSWORD
    fi

    # 默认值兜底（main.go / config.template.yaml 用到）
    : "${NEARLINK_STORAGE_DRIVER:=postgres}"
    : "${NEARLINK_HTTP_ADDR:=:8080}"
    : "${NEARLINK_LOG_LEVEL:=info}"
    : "${NEARLINK_OFFLINE_TTL:=336h}"
    : "${NEARLINK_MAX_MSG_BYTES:=65536}"
    : "${NEARLINK_ACME_EMAIL:=admin@example.com}"

    # 根据 driver 自动构造 DSN
    case "$NEARLINK_STORAGE_DRIVER" in
        postgres)
            export NEARLINK_STORAGE_DSN="postgres://nearlink:${POSTGRES_PASSWORD}@db:5432/nearlink?sslmode=disable"
            ;;
        sqlite)
            export NEARLINK_STORAGE_DSN="file:/var/lib/nearlink/nearlink.db?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)"
            ;;
        *) die "未知 NEARLINK_STORAGE_DRIVER=$NEARLINK_STORAGE_DRIVER（支持 postgres|sqlite）" ;;
    esac

    export NEARLINK_DOMAIN NEARLINK_ACME_EMAIL NEARLINK_STORAGE_DRIVER \
           NEARLINK_HTTP_ADDR NEARLINK_LOG_LEVEL NEARLINK_OFFLINE_TTL NEARLINK_MAX_MSG_BYTES
}

# --- 渲染 config.yaml ---
render_config() {
    step "渲染 $CONFIG_FILE"
    [[ -f "$CONFIG_TEMPLATE" ]] || die "缺少 $CONFIG_TEMPLATE"
    if [[ -f "$CONFIG_FILE" ]]; then
        # 已存在的 config.yaml 不静默覆盖；除非显式 --force 或 rebuild
        if [[ "${FORCE_RENDER:-0}" == "1" ]]; then
            cp "$CONFIG_FILE" "${CONFIG_FILE}.bak.$(date +%s)"
            warn "已备份旧 $CONFIG_FILE"
        else
            ok "$CONFIG_FILE 已存在，跳过渲染（如需更新请加 --force）"
            return
        fi
    fi
    # envsubst 仅替换我们关心的变量，避免误碰 yaml 中的 $ 字符
    if command -v envsubst >/dev/null 2>&1; then
        envsubst '${NEARLINK_HTTP_ADDR} ${NEARLINK_MAX_MSG_BYTES} ${NEARLINK_STORAGE_DRIVER} ${NEARLINK_STORAGE_DSN} ${NEARLINK_OFFLINE_TTL} ${NEARLINK_LOG_LEVEL}' \
            < "$CONFIG_TEMPLATE" > "$CONFIG_FILE"
    else
        # 回退：纯 sed 替换
        sed \
            -e "s|\${NEARLINK_HTTP_ADDR}|${NEARLINK_HTTP_ADDR}|g" \
            -e "s|\${NEARLINK_MAX_MSG_BYTES}|${NEARLINK_MAX_MSG_BYTES}|g" \
            -e "s|\${NEARLINK_STORAGE_DRIVER}|${NEARLINK_STORAGE_DRIVER}|g" \
            -e "s|\${NEARLINK_STORAGE_DSN}|${NEARLINK_STORAGE_DSN//|/\\|}|g" \
            -e "s|\${NEARLINK_OFFLINE_TTL}|${NEARLINK_OFFLINE_TTL}|g" \
            -e "s|\${NEARLINK_LOG_LEVEL}|${NEARLINK_LOG_LEVEL}|g" \
            "$CONFIG_TEMPLATE" > "$CONFIG_FILE"
    fi
    ok "已生成 $CONFIG_FILE"
}

# --- 网络/端口预检（非致命，只警告） ---
preflight() {
    step "端口预检"
    for port in 80 443; do
        if command -v ss >/dev/null 2>&1; then
            if ss -ltn 2>/dev/null | awk '{print $4}' | grep -E "[:.]${port}$" >/dev/null; then
                warn "端口 ${port} 已被占用（如果是上次的 caddy 实例可忽略）"
            fi
        fi
    done
}

# --- 部署 ---
do_up() {
    check_prereqs
    ensure_env
    render_config
    preflight

    step "构建镜像"
    "${COMPOSE[@]}" build server

    step "启动 db / server / caddy"
    "${COMPOSE[@]}" up -d

    step "等待健康检查"
    local i=0
    until curl -fsS "http://127.0.0.1/v1/health" >/dev/null 2>&1 \
       || curl -fsS "https://${NEARLINK_DOMAIN%:*}/v1/health" >/dev/null 2>&1 \
       || [[ $i -ge 30 ]]; do
        sleep 2; i=$((i+1)); printf '.'
    done
    echo

    if "${COMPOSE[@]}" ps --format '{{.Service}} {{.State}}' | awk '$2!="running"{exit 1}'; then
        ok "全部服务 running"
    else
        warn "部分服务未 running，请用 ./deploy.sh logs 查看"
    fi

    cat <<EOF

${GRN}部署完成${RST}
- 域名：     ${NEARLINK_DOMAIN}
- 健康检查： curl https://${NEARLINK_DOMAIN%:*}/v1/health  (公网)
            curl http://127.0.0.1/v1/health           (本机)
- 日志：     ./deploy.sh logs
- 状态：     ./deploy.sh status

Android 端在 设置 → 服务器地址 中填：
  wss://${NEARLINK_DOMAIN%:*}/v1/ws       (公网域名 + Caddy TLS)
  ws://<本机内网IP>:80/v1/ws              (内网测试)
EOF
}

do_down() {
    check_prereqs
    "${COMPOSE[@]}" down
    ok "已停止（数据卷保留）"
}

do_restart() {
    check_prereqs
    "${COMPOSE[@]}" restart
    ok "已重启"
}

do_logs() {
    check_prereqs
    "${COMPOSE[@]}" logs -f --tail=200
}

do_status() {
    check_prereqs
    "${COMPOSE[@]}" ps
    echo
    if curl -fsS "http://127.0.0.1/v1/health" >/dev/null 2>&1; then
        ok "本机 :80 健康检查 OK"
    else
        warn "本机 :80 健康检查失败（如果你直接走 :443，下面这条更重要）"
    fi
    if [[ -f "$ENV_FILE" ]]; then
        local d; d=$(grep -E '^NEARLINK_DOMAIN=' "$ENV_FILE" | cut -d= -f2-)
        if [[ -n "$d" && "$d" != ":80" ]]; then
            if curl -fsS "https://${d%:*}/v1/health" >/dev/null 2>&1; then
                ok "公网 https://${d%:*} 健康检查 OK"
            else
                warn "公网 https://${d%:*} 暂不可达。第一次启动证书签发可能要 1–3 分钟。"
            fi
        fi
    fi
}

do_rebuild() {
    check_prereqs
    ensure_env
    FORCE_RENDER=1 render_config
    "${COMPOSE[@]}" build --no-cache server
    "${COMPOSE[@]}" up -d
    ok "已重建并重启"
}

do_nuke() {
    check_prereqs
    warn "你即将删除所有数据卷（pgdata/serverdata/caddy_data/caddy_config）。"
    read -r -p "再次确认请输入 NUKE: " ans1
    [[ "$ans1" == "NUKE" ]] || { echo "取消。"; exit 0; }
    read -r -p "最后一次确认（输入域名 ${NEARLINK_DOMAIN:-?}): " ans2
    [[ "$ans2" == "${NEARLINK_DOMAIN:-}" ]] || { echo "取消。"; exit 0; }
    "${COMPOSE[@]}" down -v
    ok "已清空"
}

usage() {
    sed -n '2,18p' "$0"
}

cmd="${1:-up}"
shift || true
case "$cmd" in
    ""|up)     do_up "$@" ;;
    down)      do_down "$@" ;;
    restart)   do_restart "$@" ;;
    logs)      do_logs "$@" ;;
    status)    do_status "$@" ;;
    rebuild)   do_rebuild "$@" ;;
    nuke)      do_nuke "$@" ;;
    -h|--help) usage ;;
    *) echo "未知命令: $cmd"; usage; exit 2 ;;
esac
