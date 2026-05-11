package main

import (
	"context"
	"encoding/json"
	"flag"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/nearlink/nearlink-server/internal/auth"
	"github.com/nearlink/nearlink-server/internal/config"
	nllogger "github.com/nearlink/nearlink-server/internal/logger"
	"github.com/nearlink/nearlink-server/internal/presence"
	"github.com/nearlink/nearlink-server/internal/ratelimit"
	"github.com/nearlink/nearlink-server/internal/storage"
	nlws "github.com/nearlink/nearlink-server/internal/websocket"
	"go.uber.org/zap"
)

func main() {
	cfgPath := flag.String("c", "config.yaml", "path to config.yaml")
	flag.Parse()

	cfg, err := config.Load(*cfgPath)
	if err != nil {
		panic(err)
	}
	log, err := nllogger.New(cfg.Log.Level, cfg.Log.JSON)
	if err != nil {
		panic(err)
	}
	defer log.Sync()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	store, err := storage.Open(ctx, cfg)
	if err != nil {
		log.Fatal("open store failed", zap.Error(err))
	}
	defer store.Close()

	challenger := auth.New(cfg.Auth.ChallengeTTL)
	tracker := presence.New()
	limiter := ratelimit.New(cfg.RateLimit.PerDevicePerSec, cfg.RateLimit.BurstPerDevice)

	hub := nlws.NewHub(nlws.HubConfig{
		Logger:    log,
		Presence:  tracker,
		Store:     store,
		Rate:      limiter,
		MaxBytes:  cfg.WS.MaxMessageBytes,
		PingEvery: cfg.WS.PingInterval,
		PongWait:  cfg.WS.PongWait,
		WriteWait: cfg.WS.WriteWait,
	})

	up := &nlws.Upgrader{
		Hub:       hub,
		Auth:      challenger,
		Log:       log,
		ClockSkew: cfg.Auth.ClockSkew,
		MaxBytes:  cfg.WS.MaxMessageBytes,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/v1/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(200)
		_, _ = w.Write([]byte(`{"ok":true}`))
	})

	mux.HandleFunc("/v1/auth/challenge", func(w http.ResponseWriter, r *http.Request) {
		deviceID := r.URL.Query().Get("device_id")
		c, err := challenger.Issue(deviceID)
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(c)
	})

	mux.HandleFunc("/v1/ws", up.Handle)

	// 后台 GC：过期离线消息 + 限流桶
	go func() {
		t := time.NewTicker(1 * time.Hour)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-t.C:
				n, err := store.GCOffline(ctx, time.Now().Add(-cfg.Offline.TTL))
				if err == nil && n > 0 {
					log.Info("gc offline", zap.Int("removed", n))
				}
				limiter.GC(30 * time.Minute)
			}
		}
	}()

	srv := &http.Server{
		Addr:         cfg.HTTP.Addr,
		Handler:      mux,
		ReadTimeout:  cfg.HTTP.ReadTimeout,
		WriteTimeout: 0, // WS 长连接
	}

	idleConns := make(chan struct{})
	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
		<-sig
		log.Info("shutting down")
		shutdownCtx, cancelSd := context.WithTimeout(context.Background(), cfg.HTTP.ShutdownTimeout)
		defer cancelSd()
		_ = srv.Shutdown(shutdownCtx)
		cancel()
		close(idleConns)
	}()

	log.Info("nearlink-server listening", zap.String("addr", cfg.HTTP.Addr), zap.String("driver", cfg.Storage.Driver))
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal("listen", zap.Error(err))
	}
	<-idleConns
	log.Info("bye")
}
