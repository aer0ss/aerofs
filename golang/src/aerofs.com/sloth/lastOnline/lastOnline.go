package lastOnline

import (
	"sync"
	"time"
)

const BROADCAST_TIMEOUT_SECONDS = 30

type Times struct {
	m             map[string]time.Time
	lastBroadcast map[string]time.Time
	mutex         *sync.Mutex
}

func New() *Times {
	return &Times{
		m:             make(map[string]time.Time),
		lastBroadcast: make(map[string]time.Time),
		mutex:         new(sync.Mutex),
	}
}

// Return the number of seconds since the user was last seen online, or nil
// if the user has never made a request.
func (t *Times) GetElapsed(uid string, now time.Time) *uint64 {
	t.mutex.Lock()
	defer t.mutex.Unlock()

	v, ok := t.m[uid]
	if ok {
		since := uint64(now.Sub(v).Seconds())
		return &since
	} else {
		return nil
	}
}

// Return true if it has been more than BROADCAST_TIMEOUT_SECONDS since the
// last update
func (t *Times) Set(uid string) bool {
	t.mutex.Lock()
	defer t.mutex.Unlock()

	now := time.Now()
	t.m[uid] = now
	lastBroadcast, ok := t.lastBroadcast[uid]
	if !ok || now.Sub(lastBroadcast).Seconds() > BROADCAST_TIMEOUT_SECONDS {
		t.lastBroadcast[uid] = now
		return true
	}
	return false
}
