package main

import (
	"crypto/tls"
	"crypto/x509"
	"errors"
	"fmt"
	"net"
	"strings"
	"sync"
	"time"
)

type Downstream interface {
	Send(msg []byte) error
	Close()
}

type DiscardStream struct{}

func (s *DiscardStream) Send(d []byte) error { return nil }
func (s *DiscardStream) Close()              {}

type message struct {
	d []byte
	f chan<- error
}

type NewLineDelimitedStream struct {
	addr string
	cfg  *tls.Config
	conn net.Conn
	q    chan message
	w    sync.WaitGroup
}

func (s *NewLineDelimitedStream) connection() (net.Conn, error) {
	if s.conn == nil {
		fmt.Println("reconnect")
		var err error
		var errChannel chan error
		dialer := &net.Dialer{Timeout: 10 * time.Second}

		// TLS handshake timeout
		if s.cfg != nil {
			errChannel = make(chan error, 2)
			time.AfterFunc(dialer.Timeout, func() {
				errChannel <- errors.New("TLS handshake timeout")
			})
		}

		c, err := dialer.Dial("tcp", s.addr)
		if err != nil {
			return nil, err
		}

		if s.cfg != nil {
			tls := tls.Client(c, s.cfg)
			go func() {
				errChannel <- tls.Handshake()
			}()
			// wait for TLS handshake completion or timeout
			err = <-errChannel
			if err != nil {
				c.Close()
				return nil, err
			}
			s.conn = tls
		} else {
			s.conn = c
		}

		// set socket options
		if tcp, ok := c.(*net.TCPConn); ok {
			tcp.SetNoDelay(true)
			tcp.SetKeepAlive(true)
			tcp.SetWriteBuffer(0)
		}
	}
	return s.conn, nil
}

func (s *NewLineDelimitedStream) onError(err error) error {
	if nerr, ok := err.(net.Error); ok {
		fmt.Println("net error", err, nerr.Timeout(), nerr.Temporary())
	} else {
		fmt.Println("error", err)
	}
	if s.conn != nil {
		s.conn.Close()
		s.conn = nil
	}
	return err
}

func (s *NewLineDelimitedStream) sendLoop() {
	defer s.w.Done()
	for {
		m := <-s.q
		if m.f == nil || m.d == nil {
			break
		}
		c, err := s.connection()
		if err != nil {
			m.f <- s.onError(err)
			continue
		}
		if _, err := c.Write(m.d); err != nil {
			m.f <- s.onError(err)
			continue
		}
		m.f <- nil
	}
	fmt.Println("stop send")
}

func (s *NewLineDelimitedStream) Send(payload []byte) error {
	f := make(chan error)
	d := make([]byte, len(payload)+1)
	copy(d, payload)
	d[len(payload)] = '\n'
	s.q <- message{d: d, f: f}
	err := <-f
	return err
}

func (s *NewLineDelimitedStream) Close() {
	close(s.q)
	s.w.Wait()
	if s.conn != nil {
		s.conn.Close()
		s.conn = nil
	}
}

func NewDownstream(c map[string]string) Downstream {
	host := c["base.audit.downstream_host"]
	if len(host) == 0 {
		fmt.Println("no downstream")
		return &DiscardStream{}
	}

	port := c["base.audit.downstream_port"]

	fmt.Println("downstream:", host, port)

	s := NewLineDelimitedStream{
		addr: host + ":" + port,
		q:    make(chan message, 10),
	}

	if strings.EqualFold("true", c["base.audit.downstream_ssl_enabled"]) {
		cert := c["base.audit.downstream_certificate"]
		// lock down TLS config to 1.2 or higher and safest available ciphersuites
		// NB: this list of ciphersuite does NOT match the one enforced in Java land
		// because go support fewer ciphersuites...
		// In particular it doesn't support any DHE ciphers:
		// https://github.com/golang/go/issues/7758
		s.cfg = &tls.Config{
			ServerName: host,
			MinVersion: tls.VersionTLS12,
			CipherSuites: []uint16{
				tls.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
				tls.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
				tls.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
				tls.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
				tls.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
				tls.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
			},
		}
		// set trust anchor, if provided
		if len(cert) > 0 {
			roots := x509.NewCertPool()
			ok := roots.AppendCertsFromPEM([]byte(cert))
			if !ok {
				panic("failed to parse root cert")
			}
			s.cfg.RootCAs = roots
		}
		fmt.Println("ssl enabled")
	}

	s.w.Add(1)
	go s.sendLoop()

	return &s
}
