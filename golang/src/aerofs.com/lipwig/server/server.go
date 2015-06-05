package server

import (
	"aerofs.com/lipwig/ssmp"
	"fmt"
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

////////////////////////////////////////////////////////////////////////////////

// server implements Server, ConnectionManager and TopicManager
type server struct {
	l    net.Listener
	auth Authenticator

	// used to cleanly Stop the goroutine spawned by Start
	w sync.WaitGroup

	// protects anoynymous and connections maps
	connection sync.Mutex

	// protects topics map
	topic sync.Mutex

	anonymous   map[Connection]Connection
	connections map[string]Connection
	topics      map[string]Topic

	dispatcher Dispatcher
}

// NewServer creates a new SSMP server from an arbitrary network Listener
// and an Authenticator.
func NewServer(l net.Listener, auth Authenticator) Server {
	s := &server{
		l:           l,
		auth:        auth,
		anonymous:   make(map[Connection]Connection),
		connections: make(map[string]Connection),
		topics:      make(map[string]Topic),
	}
	s.dispatcher = NewDispatcher(s, s)
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
	for _, c := range s.connections {
		c.Close()
	}
	for c := range s.anonymous {
		c.Close()
	}
	s.w.Wait()
}

func (s *server) serve() error {
	defer s.w.Done()
	for {
		c, err := s.l.Accept()
		if err != nil {
			// TODO: handle "too many open files"?
			return err
		}
		go s.connect(c)
	}
}

func (s *server) connect(c net.Conn) {
	cc, err := NewConnection(c, s.auth, s.dispatcher)
	if err != nil {
		fmt.Println("connect rejected:", err)
		if err == ErrUnauthorized {
			c.Write(respUnauthorized)
		} else {
			c.Write(respBadRequest)
		}
		c.Close()
		return
	}
	var old Connection = nil
	s.connection.Lock()
	if cc.User() == ssmp.Anonymous {
		s.anonymous[cc] = cc
	} else {
		old = s.connections[cc.User()]
		s.connections[cc.User()] = cc
	}
	s.connection.Unlock()
	if old != nil {
		old.Close()
	}
}

func (s *server) GetConnection(user []byte) Connection {
	s.connection.Lock()
	c := s.connections[string(user)]
	s.connection.Unlock()
	return c
}

func (s *server) RemoveConnection(c Connection) {
	s.connection.Lock()
	if c.User() == ssmp.Anonymous {
		delete(s.anonymous, c)
	} else if s.connections[c.User()] == c {
		delete(s.connections, c.User())
	} else {
		fmt.Println("mismatching connection closed", c.User())
	}
	s.connection.Unlock()
}

func (s *server) GetOrCreateTopic(name []byte) Topic {
	s.topic.Lock()
	t := s.topics[string(name)]
	if t == nil {
		t = NewTopic(string(name), s)
		s.topics[string(name)] = t
	}
	s.topic.Unlock()
	return t
}

func (s *server) GetTopic(name []byte) Topic {
	s.topic.Lock()
	t := s.topics[string(name)]
	s.topic.Unlock()
	return t
}

func (s *server) RemoveTopic(name string) {
	s.topic.Lock()
	delete(s.topics, name)
	s.topic.Unlock()
}
