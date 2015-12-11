package auth

import (
	"sync"
	"time"
)

type TokenCache struct {
	ttl     time.Duration
	maxSize int
	lock    sync.Mutex
	cache   map[string]cacheEntry
}

type cacheEntry struct {
	uid  string
	time time.Time
}

func NewTokenCache(ttl time.Duration, maxSize int) *TokenCache {
	return &TokenCache{
		ttl:     ttl,
		maxSize: maxSize,
		lock:    sync.Mutex{},
		cache:   make(map[string]cacheEntry),
	}
}

// Return (uid, true) if the token is in the cache
// Return ("", false) if the token is not in the cache
func (c *TokenCache) Get(token string) (string, bool) {
	c.lock.Lock()
	defer c.lock.Unlock()
	result, ok := c.cache[token]
	if !ok || time.Now().Sub(result.time) > c.ttl {
		delete(c.cache, token)
		return "", false
	}
	return result.uid, true
}

func (c *TokenCache) Set(token string, uid string) {
	c.lock.Lock()
	defer c.lock.Unlock()
	if len(c.cache) >= c.maxSize {
		c.evict()
	}
	c.cache[token] = cacheEntry{uid: uid, time: time.Now()}
}

// Only call this with the lock held!
func (c *TokenCache) evict() {
	// Go purposefully iterates through maps in a random (not just arbitrary)
	// order. This algorithm (stolen from Redis) chooses three random entries
	// and evicts the oldest.
	var oldestKey string
	var oldestEntry cacheEntry
	checked := 0
	for key, entry := range c.cache {
		if checked == 0 || entry.time.Before(oldestEntry.time) {
			oldestKey = key
			oldestEntry = entry
		}
		checked += 1
		if checked == min(3, c.maxSize) {
			delete(c.cache, oldestKey)
			return
		}
	}
}

func min(a, b int) int {
	if a < b {
		return a
	} else {
		return b
	}
}
