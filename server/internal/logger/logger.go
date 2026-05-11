package logger

import (
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

// New 构造 zap logger。json=true 走结构化 JSON（生产），否则 console（开发）。
//
// 日志安全约束：
//   - 永远不要打印 ciphertext / nonce / pubkey 等可识别字节；如果要打印 device_id，最多打印前 8 位。
//   - 不要打印 Authorization / X-NL-Auth 头部明文。
func New(level string, json bool) (*zap.Logger, error) {
	var lvl zapcore.Level
	if err := lvl.UnmarshalText([]byte(level)); err != nil {
		lvl = zapcore.InfoLevel
	}
	cfg := zap.NewProductionConfig()
	if !json {
		cfg = zap.NewDevelopmentConfig()
		cfg.EncoderConfig.EncodeLevel = zapcore.CapitalColorLevelEncoder
	}
	cfg.Level = zap.NewAtomicLevelAt(lvl)
	cfg.DisableStacktrace = true
	return cfg.Build()
}

// Short 用于显示 device_id 时只保留前 8 字符，便于日志关联又不泄露完整 id。
func Short(deviceID string) string {
	if len(deviceID) <= 8 {
		return deviceID
	}
	return deviceID[:8] + "…"
}
