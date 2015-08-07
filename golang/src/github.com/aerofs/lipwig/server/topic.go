// Copyright (c) 2015, Air Computing Inc. <oss@aerofs.com>
// All rights reserved.

package server

import (
	"sync"
)

type TopicVisitor func(c Connection, wantsPresence bool)

// Topic represents a SSMP multicast topic.
//
// All methods can be safely called from multiple goroutines simultaneously.
type Topic interface {
	// Name returns the identifier of the topic.
	Name() string

	// Subscribe adds a connection to the set of subscribers.
	// The presence flag indicates whether the connection is interested in
	// receiving presence events about other subscribers.
	// It returns true if a new subscription was made, or false if the
	// connection was already subscribed to the topic.
	Subscribe(c Connection, presence bool) bool

	// Unsubscribe removes a connection from the set of subscribers.
	// It returns true if the connection was unsubscribed, or false it it
	// wasn't subscribed to the topic.
	Unsubscribe(c Connection) bool

	// ForAll executes v once for every subscribers.
	ForAll(v TopicVisitor)
}

////////////////////////////////////////////////////////////////////////////////

type topic struct {
	name string
	tm   TopicManager
	l    sync.RWMutex
	c    map[Connection]bool
}

// NewTopic creates a new Topic with a given name.
// The topic keeps track of the TopicManager to self-harvest when the last
// subscriber set becomes empty.
func NewTopic(name string, tm TopicManager) Topic {
	return &topic{
		name: name,
		tm:   tm,
		c:    make(map[Connection]bool),
	}
}

func (t *topic) Name() string {
	return t.name
}

func (t *topic) Subscribe(c Connection, presence bool) bool {
	t.l.Lock()
	_, subscribed := t.c[c]
	if !subscribed {
		t.c[c] = presence
	}
	t.l.Unlock()
	return !subscribed
}

func (t *topic) Unsubscribe(c Connection) bool {
	t.l.Lock()
	_, subscribed := t.c[c]
	delete(t.c, c)
	t.l.Unlock()
	if len(t.c) == 0 {
		t.tm.RemoveTopic(t.name)
	}
	return subscribed
}

func (t *topic) ForAll(v TopicVisitor) {
	t.l.RLock()
	defer t.l.RUnlock()
	for c, presence := range t.c {
		v(c, presence)
	}
}
