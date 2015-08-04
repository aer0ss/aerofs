// Copyright (c) 2015, Air Computing Inc. <oss@aerofs.com>
// All rights reserved.

package server

import (
	"aerofs.com/lipwig/ssmp"
	"bytes"
	"fmt"
	"sync"
)

// A ConnectionManager manages a set of Connection.
// All methods are safe to call from multiple goroutines simultaneously.
type ConnectionManager interface {
	GetConnection(user []byte) Connection
	RemoveConnection(c Connection)
}

// A Dispatcher parses incoming requests and reacts to them appropriately.
// All methods are safe to call from multiple goroutines simultaneously.
type Dispatcher interface {
	ConnectionManager

	// Dispatch parses req, reacts appropriately and sends a response to c.
	Dispatch(c Connection, req []byte)
}

// A TopicManager manages a set of Topic.
// All methods are safe to call from multiple goroutines simultaneously.
type TopicManager interface {
	GetTopic(name []byte) Topic
	GetOrCreateTopic(name []byte) Topic
	RemoveTopic(name string)
}

////////////////////////////////////////////////////////////////////////////////

type dispatcher struct {
	topics      TopicManager
	connections ConnectionManager
	handlers    map[string]handler

	bufPool sync.Pool
}

// NewDispatcher creates a SSMP dispatcher using the given TopicManager and ConnectionManager.
func NewDispatcher(topics TopicManager, connections ConnectionManager) Dispatcher {
	return &dispatcher{
		topics:      topics,
		connections: connections,
		handlers: map[string]handler{
			ssmp.SUBSCRIBE:   h(onSubscribe, fieldTo|fieldOption),
			ssmp.UNSUBSCRIBE: h(onUnsubscribe, fieldTo),
			ssmp.UCAST:       h(onUcast, fieldTo|fieldPayload),
			ssmp.MCAST:       h(onMcast, fieldTo|fieldPayload),
			ssmp.BCAST:       h(onBcast, fieldPayload),
			ssmp.PING:        h(onPing, 0),
			ssmp.PONG:        h(onPong, 0),
			ssmp.CLOSE:       h(onClose, 0),
		},
		bufPool: sync.Pool{
			New: func() interface{} {
				return new(bytes.Buffer)
			},
		},
	}
}

func (d *dispatcher) Dispatch(c Connection, s []byte) {
	cmd := ssmp.NewCommand(s)
	verb, err := ssmp.VerbField(cmd)
	if err != nil {
		fmt.Println("invalid verb:", err)
		c.Write(respBadRequest)
		return
	}
	h := d.handlers[string(verb)]
	if h.h == nil {
		fmt.Println("unsupported command:", verb)
		c.Write(respBadRequest)
		return
	}
	var to []byte
	var payload []byte
	if (h.f & fieldTo) != 0 {
		if to, err = ssmp.IdField(cmd); err != nil {
			c.Write(respBadRequest)
			return
		}
	}
	if (h.f & fieldPayload) != 0 {
		extract := ssmp.PayloadField
		if (h.f & fieldOption) == fieldOption {
			extract = ssmp.OptionField
		}
		if payload, err = extract(cmd); err != nil {
			c.Write(respBadRequest)
			return
		}
	}
	if !cmd.AtEnd() {
		fmt.Println("trailing data:", cmd.Trailing())
		c.Write(respBadRequest)
		return
	}
	h.h(c, to, payload, d)
}

func (d *dispatcher) GetConnection(user []byte) Connection {
	return d.connections.GetConnection(user)
}

func (d *dispatcher) RemoveConnection(c Connection) {
	d.connections.RemoveConnection(c)
}

func (d *dispatcher) buffer() *bytes.Buffer {
	buf := d.bufPool.Get().(*bytes.Buffer)
	buf.Reset()
	return buf
}

func (d *dispatcher) release(b *bytes.Buffer) {
	d.bufPool.Put(b)
}

////////////////////////////////////////////////////////////////////////////////

type handlerFunc func(Connection, []byte, []byte, *dispatcher)

const (
	fieldTo      = 1
	fieldPayload = 2
	fieldOption  = 6
)

type handler struct {
	f int32
	h handlerFunc
}

func h(h handlerFunc, f int32) handler {
	return handler{f: f, h: h}
}

func onSubscribe(c Connection, n, option []byte, d *dispatcher) {
	from := c.User()
	if from == ssmp.Anonymous {
		c.Write(respNotAllowed)
		return
	}
	presence := ssmp.Equal(option, ssmp.PRESENCE)
	if len(option) > 0 && !presence {
		fmt.Println("unrecognized option:", option)
		c.Write(respBadRequest)
		return
	}
	t := d.topics.GetOrCreateTopic(n)
	t.Subscribe(c, presence)
	c.Subscribe(t)
	c.Write(respOk)

	// notify existing subscribers of new sub
	buf := d.buffer()
	buf.Grow(16 + len(from) + len(n))
	buf.WriteString(respEvent)
	buf.WriteString(from)
	buf.WriteByte(' ')
	buf.WriteString(ssmp.SUBSCRIBE)
	buf.WriteByte(' ')
	buf.Write(n)
	buf.WriteByte('\n')
	event := buf.Bytes()

	var buf2 *bytes.Buffer = nil
	if presence {
		buf2 = d.buffer()
	}

	t.ForAll(func(cc Connection, wantsPresence bool) {
		if c == cc {
			return
		}
		if wantsPresence {
			cc.Write(event)
		}
		if presence {
			buf2.WriteString(respEvent)
			buf2.WriteString(cc.User())
			buf2.Write(event[4+len(from):])
			if buf2.Len() > 512 {
				c.Write(buf2.Bytes())
				buf2.Reset()
			}
		}
	})
	d.release(buf)
	if buf2 != nil {
		if buf2.Len() > 0 {
			c.Write(buf2.Bytes())
		}
		d.release(buf2)
	}
}

func onUnsubscribe(c Connection, n, _ []byte, d *dispatcher) {
	from := c.User()
	if from == ssmp.Anonymous {
		c.Write(respNotAllowed)
		return
	}
	t := d.topics.GetTopic(n)
	if t != nil {
		c.Unsubscribe(n)
		t.Unsubscribe(c)
		buf := d.buffer()
		buf.Grow(18 + len(from) + len(n))
		buf.WriteString(respEvent)
		buf.WriteString(from)
		buf.WriteByte(' ')
		buf.WriteString(ssmp.UNSUBSCRIBE)
		buf.WriteByte(' ')
		buf.Write(n)
		buf.WriteByte('\n')
		event := buf.Bytes()
		t.ForAll(func(cc Connection, wantsPresence bool) {
			if wantsPresence {
				cc.Write(event)
			}
		})
		d.release(buf)
		c.Write(respOk)
	} else {
		c.Write(respNotFound)
	}
}

func onBcast(c Connection, _, payload []byte, d *dispatcher) {
	from := c.User()
	if from == ssmp.Anonymous {
		c.Write(respNotAllowed)
		return
	}
	buf := d.buffer()
	buf.Grow(12 + len(from) + len(payload))
	buf.WriteString(respEvent)
	buf.WriteString(from)
	buf.WriteByte(' ')
	buf.WriteString(ssmp.BCAST)
	buf.WriteByte(' ')
	buf.Write(payload)
	buf.WriteByte('\n')
	c.Broadcast(buf.Bytes())
	d.release(buf)
	c.Write(respOk)
}

func onUcast(c Connection, u, payload []byte, d *dispatcher) {
	from := c.User()
	cc := d.connections.GetConnection(u)
	if cc == nil {
		c.Write(respNotFound)
	} else {
		buf := d.buffer()
		buf.Grow(12 + len(from) + len(payload))
		buf.WriteString(respEvent)
		buf.WriteString(from)
		buf.WriteByte(' ')
		buf.WriteString(ssmp.UCAST)
		buf.WriteByte(' ')
		buf.Write(payload)
		buf.WriteByte('\n')
		cc.Write(buf.Bytes())
		d.release(buf)
		c.Write(respOk)
	}
}

func onMcast(c Connection, n, payload []byte, d *dispatcher) {
	from := c.User()
	t := d.topics.GetTopic(n)
	if t == nil {
		c.Write(respNotFound)
	} else {
		buf := d.buffer()
		buf.Grow(13 + len(from) + len(n) + len(payload))
		buf.WriteString(respEvent)
		buf.WriteString(from)
		buf.WriteByte(' ')
		buf.WriteString(ssmp.MCAST)
		buf.WriteByte(' ')
		buf.Write(n)
		buf.WriteByte(' ')
		buf.Write(payload)
		buf.WriteByte('\n')
		msg := buf.Bytes()
		t.ForAll(func(cc Connection, _ bool) {
			if c != cc {
				cc.Write(msg)
			}
		})
		d.release(buf)
		c.Write(respOk)
	}
}

var pong []byte = []byte(respEvent + ". " + ssmp.PONG + "\n")

func onPing(c Connection, _, _ []byte, _ *dispatcher) {
	c.Write(pong)
}

func onPong(c Connection, _, _ []byte, _ *dispatcher) {
	// nothing to see here...
}

func onClose(c Connection, _, _ []byte, _ *dispatcher) {
	c.Write(respOk)
	c.Close()
}
