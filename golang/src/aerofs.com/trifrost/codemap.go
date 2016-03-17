package main

import (
	"crypto/rand"
	"log"
	"math/big"
	"sync"
)

// CodeMap stores (email, code) pairs and allows thread-safe constant-time
// lookup of either.
type CodeMap interface {

	// Generate a code for an email address, reusing any outstanding code that
	// exists for that email. GetCode will always return a code.
	GetCode(email string) int

	// PopCode validates the (email, code) pair. If valid, it removes it from
	// the map and returns true.
	PopCode(email string, code int) bool
}

type codeMap struct {
	m    map[string]int
	lock sync.Mutex
}

func (cm *codeMap) GetCode(email string) int {
	cm.lock.Lock()
	defer cm.lock.Unlock()

	if code, ok := cm.m[email]; ok {
		return code
	}

	c, err := rand.Int(rand.Reader, big.NewInt(1000000))
	code := int(c.Int64())
	if err != nil {
		log.Panic(err)
	}
	cm.m[email] = code
	return code
}

func (cm *codeMap) PopCode(email string, code int) bool {
	cm.lock.Lock()
	defer cm.lock.Unlock()

	c, ok := cm.m[email]
	if ok && c == code {
		delete(cm.m, email)
		return true
	}
	return false
}

func NewCodeMap() CodeMap {
	return &codeMap{m: make(map[string]int)}
}
