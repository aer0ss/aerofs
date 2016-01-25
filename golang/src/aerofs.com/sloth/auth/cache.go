package auth

import (
	"sync"
	"time"
)

type verifyResponse struct {
	uid string
	err error
}

type TokenCache struct {
	ttl     time.Duration
	maxSize int
	lock    sync.Mutex
	cache   map[string]*cacheEntry
}

type cacheEntry struct {
	response     verifyResponse
	time         time.Time
	pendingQueue []chan verifyResponse
}

// Deliver to all blocked futures and empty cache
// Only call with cacheEntry mutex locked
func (ce *cacheEntry) flush() {
	for _, v := range ce.pendingQueue {
		v <- ce.response
	}
	ce.pendingQueue = nil
}

func NewTokenCache(ttl time.Duration, maxSize int) *TokenCache {
	return &TokenCache{
		ttl:     ttl,
		maxSize: maxSize,
		lock:    sync.Mutex{},
		cache:   make(map[string]*cacheEntry),
	}
}

// Return <uid, nil> if the token is in the cache
// Return <"", tokenNotFoundError> if invalid token with no UID
// Return <"", error> if transport/marshalling error
func (c *TokenCache) Get(token string) chan verifyResponse {
	c.lock.Lock()
	defer c.lock.Unlock()

	if len(c.cache) > c.maxSize {
		c.evict()
	}

	fut := make(chan verifyResponse, 1)
	entry, present := c.cache[token]

	switch {
	// Replace out of date entry
	case present && time.Now().Sub(entry.time) > c.ttl:
		fallthrough

	// Request new value if not in Cache
	case !present:
		c.cache[token] = &cacheEntry{time: time.Now()}

		go func() {
			uid, err := requestVerify(token)
			c.lock.Lock()
			defer c.lock.Unlock()

			tokenEntry := c.cache[token]
			tokenEntry.response = verifyResponse{uid, err}
			tokenEntry.flush()

			if _, notFound := err.(TokenNotFoundError); !notFound && err != nil {
				delete(c.cache, token)
			}
		}()

		c.cache[token].pendingQueue = append(c.cache[token].pendingQueue, fut)

	// Blocked on waiting for a request
	case present && len(entry.pendingQueue) > 0:
		c.cache[token].pendingQueue = append(c.cache[token].pendingQueue, fut)

	// Return existing, correct entry
	case present:
		fut <- entry.response
	}

	return fut
}

func (c *TokenCache) Delete(token string) {
	c.lock.Lock()
	defer c.lock.Unlock()

	entry, ok := c.cache[token]
	if !ok {
		return
	}

	// Do not delete entries that are waiting on a pending request
	if len(entry.pendingQueue) == 0 {
		delete(c.cache, token)
	}
}

// Evict an entry from the cache to prevent memory leak
// Only call this with the lock held!
func (c *TokenCache) evict() {
	// Go purposefully iterates through maps in a random (not just arbitrary)
	// order. This algorithm (stolen from Redis) chooses three random entries
	// and evicts the oldest. If the oldest is a PendingRequest, don't kill

	var oldestKey string
	var oldestEntry *cacheEntry
	checked := 0
	for key, entry := range c.cache {
		if checked == 0 || entry.time.Before(oldestEntry.time) {
			oldestKey = key
			oldestEntry = entry
		}
		checked += 1
		if checked == min(3, c.maxSize) && len(c.cache[oldestKey].pendingQueue) == 0 {
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
