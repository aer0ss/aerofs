package test

import (
	"bufio"
	"crypto"
	"crypto/tls"
	"net"
	"net/textproto"
	"strconv"
	"sync"
)

type Endpoint struct {
	l   net.Listener
	p   int
	q   chan bool
	w   sync.WaitGroup
	c   []net.Conn
	tls *tls.Config
}

func (e *Endpoint) ListeningPort() int {
	return e.l.Addr().(*net.TCPAddr).Port
}

func (e *Endpoint) accept(ch chan<- net.Conn) {
	defer close(ch)
	defer e.w.Done()
	for {
		c, err := e.l.Accept()
		if err != nil {
			break
		}
		ch <- c
	}
}

func (e *Endpoint) read(c net.Conn, out chan<- string) {
	defer e.w.Done()
	b := textproto.NewReader(bufio.NewReader(c))
	for {
		l, err := b.ReadLine()
		if err != nil {
			c.Close()
			break
		}
		out <- l
	}
}

func (e *Endpoint) acceptLoop(out chan<- string) {
	defer e.w.Done()
	ch := make(chan net.Conn)
	e.w.Add(1)
	go e.accept(ch)
	for {
		c := <-ch
		if c == nil {
			break
		}
		e.c = append(e.c, c)
		e.w.Add(1)
		go e.read(c, out)
	}
}

func (e *Endpoint) Start() <-chan string {
	l, err := net.Listen("tcp", "127.0.0.1:"+strconv.Itoa(e.p))
	if err != nil {
		panic(err)
	}
	if e.tls != nil {
		l = tls.NewListener(l, e.tls)
	}
	e.l = l
	out := make(chan string, 10)
	e.c = make([]net.Conn, 0, 10)
	e.w.Add(1)
	go e.acceptLoop(out)
	return out
}

func (e *Endpoint) Stop() {
	if e.c == nil {
		return
	}
	// remember selected port for subsequent Start()
	e.p = e.ListeningPort()
	e.l.Close()
	for _, c := range e.c {
		c.Close()
	}
	e.c = nil
	e.w.Wait()
}

func NewEndpoint() *Endpoint {
	return &Endpoint{}
}

func NewSslEndpoint(priv crypto.PrivateKey, cert []byte) *Endpoint {
	e := NewEndpoint()
	e.tls = &tls.Config{
		Certificates: []tls.Certificate{
			tls.Certificate{PrivateKey: priv, Certificate: [][]byte{cert}},
		},
	}
	return e
}
