# --- 构建阶段 ---
FROM golang:1.22-alpine AS build
RUN apk add --no-cache git ca-certificates gcc musl-dev sqlite-dev
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download || true
COPY . .
# CGO 启用以支持 mattn/go-sqlite3；如只用 postgres 可改成 CGO_ENABLED=0
ENV CGO_ENABLED=1
RUN go build -trimpath -ldflags="-s -w" -o /out/nearlink-server ./cmd/nearlink-server

# --- 运行阶段 ---
FROM alpine:3.20
RUN apk add --no-cache ca-certificates tzdata sqlite-libs && \
    addgroup -S nearlink && adduser -S -G nearlink nearlink && \
    mkdir -p /var/lib/nearlink && chown -R nearlink:nearlink /var/lib/nearlink
USER nearlink
WORKDIR /app
COPY --from=build /out/nearlink-server /usr/local/bin/nearlink-server
COPY config.example.yaml /app/config.yaml
EXPOSE 8080
VOLUME ["/var/lib/nearlink"]
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/v1/health || exit 1
ENTRYPOINT ["/usr/local/bin/nearlink-server", "-c", "/app/config.yaml"]
