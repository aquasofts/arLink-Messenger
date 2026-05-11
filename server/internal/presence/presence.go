package presence

import (
	"sync"
	"time"
)

// Tracker 持久化在内存里：每个 device 最后一次在线时间。
// 对端订阅时立刻把当前状态推回去；状态变化广播给订阅者。
type Tracker struct {
	mu       sync.RWMutex
	online   map[string]struct{}
	lastSeen map[string]int64

	subsMu sync.Mutex
	subs   map[string]map[string]chan Event // subscriber -> targetDevice -> chan
}

type Event struct {
	DeviceID string
	Online   bool
	LastSeen int64
}

func New() *Tracker {
	return &Tracker{
		online:   map[string]struct{}{},
		lastSeen: map[string]int64{},
		subs:     map[string]map[string]chan Event{},
	}
}

func (t *Tracker) Connect(deviceID string) {
	t.mu.Lock()
	t.online[deviceID] = struct{}{}
	t.lastSeen[deviceID] = time.Now().UnixMilli()
	t.mu.Unlock()
	t.broadcast(deviceID, true)
}

func (t *Tracker) Disconnect(deviceID string) {
	t.mu.Lock()
	delete(t.online, deviceID)
	t.lastSeen[deviceID] = time.Now().UnixMilli()
	t.mu.Unlock()
	t.broadcast(deviceID, false)
}

func (t *Tracker) IsOnline(deviceID string) bool {
	t.mu.RLock()
	_, ok := t.online[deviceID]
	t.mu.RUnlock()
	return ok
}

func (t *Tracker) Snapshot(deviceID string) (online bool, lastSeen int64) {
	t.mu.RLock()
	defer t.mu.RUnlock()
	_, online = t.online[deviceID]
	lastSeen = t.lastSeen[deviceID]
	return
}

// Subscribe 让 `subscriber` 关注一组 `targets` 的状态变化。
// 返回的 channel 必须由调用方持续消费，否则会阻塞。
func (t *Tracker) Subscribe(subscriber string, targets []string) <-chan Event {
	ch := make(chan Event, 32)
	t.subsMu.Lock()
	if _, ok := t.subs[subscriber]; !ok {
		t.subs[subscriber] = map[string]chan Event{}
	}
	for _, target := range targets {
		t.subs[subscriber][target] = ch
	}
	t.subsMu.Unlock()
	// 立即推送当前快照
	go func() {
		for _, target := range targets {
			online, last := t.Snapshot(target)
			select {
			case ch <- Event{DeviceID: target, Online: online, LastSeen: last}:
			default:
			}
		}
	}()
	return ch
}

func (t *Tracker) Unsubscribe(subscriber string) {
	t.subsMu.Lock()
	delete(t.subs, subscriber)
	t.subsMu.Unlock()
}

func (t *Tracker) broadcast(deviceID string, online bool) {
	last, _ := t.lastSeenMs(deviceID)
	ev := Event{DeviceID: deviceID, Online: online, LastSeen: last}
	t.subsMu.Lock()
	for _, m := range t.subs {
		if ch, ok := m[deviceID]; ok {
			select {
			case ch <- ev:
			default: /* drop, 订阅者太慢 */
			}
		}
	}
	t.subsMu.Unlock()
}

func (t *Tracker) lastSeenMs(deviceID string) (int64, bool) {
	t.mu.RLock()
	v, ok := t.lastSeen[deviceID]
	t.mu.RUnlock()
	return v, ok
}
