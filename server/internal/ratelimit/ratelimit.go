package ratelimit

import (
	"sync"
	"time"

	"golang.org/x/time/rate"
)

// Limiter 是 per-device 的令牌桶。
// 内存实现；大规模生产请用 Redis + 全局令牌桶。
type Limiter struct {
	perSec int
	burst  int
	mu     sync.Mutex
	buckets map[string]*rate.Limiter
}

func New(perSec, burst int) *Limiter {
	if perSec <= 0 {
		perSec = 10
	}
	if burst <= 0 {
		burst = perSec * 3
	}
	return &Limiter{
		perSec:  perSec,
		burst:   burst,
		buckets: map[string]*rate.Limiter{},
	}
}

func (l *Limiter) Allow(deviceID string) bool {
	l.mu.Lock()
	b, ok := l.buckets[deviceID]
	if !ok {
		b = rate.NewLimiter(rate.Limit(l.perSec), l.burst)
		l.buckets[deviceID] = b
	}
	l.mu.Unlock()
	return b.Allow()
}

// GC 清理 idle 令牌桶。可由 ticker 周期调用。
func (l *Limiter) GC(idle time.Duration) {
	// rate.Limiter 没有 last-access 时间戳；保留为 TODO。
	// 当前内存占用与活跃 device 数线性，对小规模部署可忽略。
	_ = idle
}
