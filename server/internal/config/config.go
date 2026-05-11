package config

import (
	"errors"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

type Config struct {
	HTTP struct {
		Addr            string        `yaml:"addr"`
		ReadTimeout     time.Duration `yaml:"read_timeout"`
		WriteTimeout    time.Duration `yaml:"write_timeout"`
		ShutdownTimeout time.Duration `yaml:"shutdown_timeout"`
	} `yaml:"http"`

	WS struct {
		MaxMessageBytes int           `yaml:"max_message_bytes"`
		PingInterval    time.Duration `yaml:"ping_interval"`
		PongWait        time.Duration `yaml:"pong_wait"`
		WriteWait       time.Duration `yaml:"write_wait"`
	} `yaml:"ws"`

	Auth struct {
		ChallengeTTL time.Duration `yaml:"challenge_ttl"`
		ClockSkew    time.Duration `yaml:"clock_skew"`
	} `yaml:"auth"`

	RateLimit struct {
		PerDevicePerSec int `yaml:"per_device_per_sec"`
		BurstPerDevice  int `yaml:"burst_per_device"`
	} `yaml:"rate_limit"`

	Storage struct {
		Driver string `yaml:"driver"` // "postgres" | "sqlite"
		DSN    string `yaml:"dsn"`
	} `yaml:"storage"`

	Offline struct {
		TTL      time.Duration `yaml:"ttl"`
		MaxPerTo int           `yaml:"max_per_to"`
	} `yaml:"offline"`

	Log struct {
		Level string `yaml:"level"` // "debug" | "info" | "warn" | "error"
		JSON  bool   `yaml:"json"`
	} `yaml:"log"`
}

func Default() Config {
	c := Config{}
	c.HTTP.Addr = ":8080"
	c.HTTP.ReadTimeout = 15 * time.Second
	c.HTTP.WriteTimeout = 15 * time.Second
	c.HTTP.ShutdownTimeout = 10 * time.Second
	c.WS.MaxMessageBytes = 64 * 1024
	c.WS.PingInterval = 30 * time.Second
	c.WS.PongWait = 90 * time.Second
	c.WS.WriteWait = 10 * time.Second
	c.Auth.ChallengeTTL = 60 * time.Second
	c.Auth.ClockSkew = 5 * time.Minute
	c.RateLimit.PerDevicePerSec = 10
	c.RateLimit.BurstPerDevice = 30
	c.Storage.Driver = "sqlite"
	c.Storage.DSN = "file:nearlink.db?_pragma=journal_mode(WAL)&_pragma=busy_timeout(5000)"
	c.Offline.TTL = 14 * 24 * time.Hour
	c.Offline.MaxPerTo = 5000
	c.Log.Level = "info"
	c.Log.JSON = false
	return c
}

func Load(path string) (Config, error) {
	c := Default()
	if path == "" {
		return c, nil
	}
	b, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return c, nil
		}
		return c, err
	}
	if err := yaml.Unmarshal(b, &c); err != nil {
		return c, err
	}
	return c, nil
}
