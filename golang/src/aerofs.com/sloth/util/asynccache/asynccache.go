// Package asynccache provides an interface for an asynchronous, thread-safe
// map which abstracts away the cache miss behaviour.
package asynccache

import (
	"sync"
)

// Result contains the value being fetched, xor any error encountered while
// executing the cache miss function.
type Result struct {
	Val   string // TODO: use interface{} ?
	Error error
}

// Map is an interface which wraps an async, thread-safe Get method which
// invokes the cache miss behaviour if necessary.
type Map interface {

	// Get returns a channel on which the result will be sent. The result is
	// sent instantly if the key is present in the cache; otherwise, the cache
	// miss function is executed and the result is sent after it completes.
	Get(key string, args ...interface{}) <-chan Result
}

// impl implements this package's Map interface
type impl struct {
	vals      map[string]string
	waiting   map[string][]chan Result
	lock      sync.Mutex
	cacheMiss func(key string, args ...interface{}) (string, error)
}

func (i *impl) Get(key string, args ...interface{}) <-chan Result {
	res := make(chan Result, 1)

	i.lock.Lock()
	defer i.lock.Unlock()

	val, ok := i.vals[key]
	if ok {
		// value exists in cache; return it
		res <- Result{Val: val}
	} else {
		waiting := i.waiting[key]
		if waiting == nil {
			// first request for this key; call cacheMiss()
			go i.onCacheMiss(key, args...)
		}
		// wait for result
		waiting = append(waiting, res)
		i.waiting[key] = waiting
	}

	return res
}

func (i *impl) onCacheMiss(key string, args ...interface{}) {
	// execute cache miss behaviour
	var r Result
	r.Val, r.Error = i.cacheMiss(key, args...)

	i.lock.Lock()
	defer i.lock.Unlock()

	// persist result on success
	if r.Error != nil {
		i.vals[key] = r.Val
	}

	// either way, send result to all on waiting list
	waiting := i.waiting[key]
	if len(waiting) == 0 {
		panic("bad state: no waiting list for " + key)
	}
	for _, c := range waiting {
		c <- r
	}
	delete(i.waiting, key)
}

// New returns a new Map with the given cache miss behaviour
func New(cacheMiss func(string, ...interface{}) (string, error)) Map {
	return &impl{
		vals:      make(map[string]string),
		waiting:   make(map[string][]chan Result),
		cacheMiss: cacheMiss,
	}
}
