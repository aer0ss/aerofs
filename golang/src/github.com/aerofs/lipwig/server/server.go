// Copyright (c) 2015, Air Computing Inc. <oss@aerofs.com>
// All rights reserved.

package server

import (
	"crypto/tls"
	"fmt"
	"github.com/aerofs/lipwig/ssmp"
	"net"
	"sync"
)

type Server interface {
	// Serve accept connections in the calling goroutine and only returns
	// in case of error.
	Serve() error

	// Start accepts connection in new goroutine and returns the Server
	// This allows the following terse idiom:
	//		defer s.Start().Stop()
	Start() Server

	// ListeningPort returns the TCP port to which the underlying Listener is
	// bound.
	// Only use this method if the Server was constructed from a TCP Listener,
	// otherwise it will result in a panic.
	ListeningPort() int

	// Stop stops the goroutine spawned by Start.
	// Do not use for servers started with Serve.
	Stop()
}

// A ConnectionManager manages a set of Connection.
// All methods are safe to call from multiple goroutines simultaneously.
type ConnectionManager struct {
	connection  sync.Mutex
	anonymous   map[*Connection]*Connection
	connections map[string]*Connection
}

// A TopicManager manages a set of Topic.
// All methods are safe to call from multiple goroutines simultaneously.
type TopicManager struct {
	topic  sync.Mutex
	topics map[string]*Topic
}

////////////////////////////////////////////////////////////////////////////////

// server implements Server, ConnectionManager and TopicManager
type server struct {
	ConnectionManager
	TopicManager

	l    *net.TCPListener
	cfg  *tls.Config
	auth Authenticator

	// used to cleanly Stop the goroutine spawned by Start
	w sync.WaitGroup

	dispatcher *Dispatcher
}

// NewServer creates a new SSMP server from an arbitrary network Listener
// and an Authenticator.
func NewServer(l net.Listener, auth Authenticator, cfg *tls.Config) Server {
	s := &server{
		l:    l.(*net.TCPListener),
		cfg:  cfg,
		auth: auth,
		ConnectionManager: ConnectionManager{
			anonymous:   make(map[*Connection]*Connection),
			connections: make(map[string]*Connection),
		},
		TopicManager: TopicManager{
			topics: make(map[string]*Topic),
		},
	}
	s.dispatcher = NewDispatcher(&s.TopicManager, &s.ConnectionManager)
	return s
}

func (s *server) Serve() error {
	s.w.Add(1)
	return s.serve()
}

func (s *server) Start() Server {
	s.w.Add(1)
	go s.serve()
	return s
}

func (s *server) ListeningPort() int {
	return s.l.Addr().(*net.TCPAddr).Port
}

func (s *server) Stop() {
	s.l.Close()
	s.connection.Lock()
	for _, c := range s.connections {
		c.Close()
	}
	s.connection.Unlock()
	s.topic.Lock()
	for c := range s.anonymous {
		c.Close()
	}
	s.topic.Unlock()
	s.w.Wait()
}

func (s *server) serve() error {
	defer s.w.Done()
	for {
		c, err := s.l.AcceptTCP()
		if err != nil {
			// TODO: handle "too many open files"?
			return err
		}
		go s.connect(s.configure(c))
	}
}

func (s *server) configure(c *net.TCPConn) net.Conn {
	c.SetNoDelay(true)
	if s.cfg == nil {
		return c
	}
	return tls.Server(c, s.cfg)
}

func (s *server) connect(c net.Conn) {
	cc, err := NewConnection(c, s.auth, s.dispatcher)
	if err != nil {
		fmt.Println("connect rejected:", err)
		if err == ErrUnauthorized {
			c.Write(s.auth.Unauthorized())
		} else if err == ErrInvalidLogin {
			c.Write(respBadRequest)
		}
		c.Close()
		return
	}
	var old *Connection
	u := cc.User
	s.connection.Lock()
	if u == ssmp.Anonymous {
		s.anonymous[cc] = cc
	} else {
		old = s.connections[u]
		s.connections[u] = cc
	}
	s.connection.Unlock()
	if old != nil {
		old.Close()
	}
}

func (s *ConnectionManager) GetConnection(user []byte) *Connection {
	s.connection.Lock()
	c := s.connections[string(user)]
	s.connection.Unlock()
	return c
}

func (s *ConnectionManager) RemoveConnection(c *Connection) {
	s.connection.Lock()
	if c.User == ssmp.Anonymous {
		delete(s.anonymous, c)
	} else if s.connections[c.User] == c {
		delete(s.connections, c.User)
	} else {
		fmt.Println("mismatching connection closed", c.User)
	}
	s.connection.Unlock()
}

func (s *TopicManager) GetOrCreateTopic(name []byte) *Topic {
	s.topic.Lock()
	t := s.topics[string(name)]
	if t == nil {
		t = NewTopic(string(name), s)
		s.topics[string(name)] = t
	}
	s.topic.Unlock()
	return t
}

func (s *TopicManager) GetTopic(name []byte) *Topic {
	s.topic.Lock()
	t := s.topics[string(name)]
	s.topic.Unlock()
	return t
}

func (s *TopicManager) RemoveTopic(name string) {
	s.topic.Lock()
	delete(s.topics, name)
	s.topic.Unlock()
}
