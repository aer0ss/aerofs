package main

import (
	"aerofs.com/auditor/test"
	"aerofs.com/ca-server/cert"
	"encoding/pem"
	"github.com/stretchr/testify/assert"
	"strconv"
	"testing"
)

func Expect(t *testing.T, r <-chan string, expected string) {
	actual := <-r
	assert.Equal(t, expected, actual)
}

func NewStream(port int) Downstream {
	return NewDownstream(map[string]string{
		"base.audit.downstream_host":        "127.0.0.1",
		"base.audit.downstream_port":        strconv.Itoa(port),
		"base.audit.downstream_ssl_enabled": "false",
	})
}

func NewSslStream(port int, cert []byte) Downstream {
	if len(cert) > 0 {
		cert = pem.EncodeToMemory(&pem.Block{
			Type:  "CERTIFICATE",
			Bytes: cert,
		})
	}
	return NewDownstream(map[string]string{
		"base.audit.downstream_host":        "127.0.0.1",
		"base.audit.downstream_port":        strconv.Itoa(port),
		"base.audit.downstream_ssl_enabled": "true",
		"base.audit.downstream_certificate": string(cert),
	})
}

const TEST_VALUE string = "{\"This is a thing amirite\":\"Yep\"}"

func TestNewLineDelimitedStream_Send_should_fail_when_endpoint_down(t *testing.T) {
	s := NewStream(0xdead)
	defer s.Close()

	err := s.Send([]byte(TEST_VALUE))
	assert.NotNil(t, err)
}

func TestNewLineDelimitedStream_Send_should_succeed(t *testing.T) {
	e := test.NewEndpoint()
	r := e.Start()
	defer e.Stop()
	s := NewStream(e.ListeningPort())
	defer s.Close()

	err := s.Send([]byte(TEST_VALUE))
	assert.Nil(t, err)
	Expect(t, r, TEST_VALUE)
}

func TestNewLineDelimitedStream_Send_should_succeed_multiple_times(t *testing.T) {
	e := test.NewEndpoint()
	r := e.Start()
	defer e.Stop()
	s := NewStream(e.ListeningPort())
	defer s.Close()

	for i := 0; i < 10; i++ {
		err := s.Send([]byte(TEST_VALUE))
		assert.Nil(t, err)
		Expect(t, r, TEST_VALUE)
	}
}

func sendUntilError(s Downstream) error {
	// sigh...  write after a disconnect silently fails until the other end sends
	// an RST and apparently nothing we can do about it...
	// see: https://github.com/golang/go/issues/10940
	for {
		err := s.Send([]byte(TEST_VALUE))
		if err != nil {
			return err
		}
	}
}

func TestNewLineDelimitedStream_Send_should_fail_after_disconnect(t *testing.T) {
	e := test.NewEndpoint()
	r := e.Start()
	defer e.Stop()
	s := NewStream(e.ListeningPort())
	defer s.Close()

	err := s.Send([]byte(TEST_VALUE))
	assert.Nil(t, err)
	Expect(t, r, TEST_VALUE)

	e.Stop()

	assert.NotNil(t, sendUntilError(s))

	// failure should not be transient
	err = s.Send([]byte(TEST_VALUE))
	assert.NotNil(t, err)
}

func TestNewLineDelimitedStream_Send_should_succeed_after_reconnect(t *testing.T) {
	e := test.NewEndpoint()
	r := e.Start()
	defer e.Stop()
	s := NewStream(e.ListeningPort())
	defer s.Close()

	err := s.Send([]byte(TEST_VALUE))
	assert.Nil(t, err)
	Expect(t, r, TEST_VALUE)

	e.Stop()
	r = e.Start()

	assert.NotNil(t, sendUntilError(s))

	// should reconnect after failure
	err = s.Send([]byte(TEST_VALUE))
	assert.Nil(t, err)
	Expect(t, r, TEST_VALUE)
}

func TestNewLineDelimitedStream_Send_ssl_should_succeed(t *testing.T) {
	priv, cert, err := cert.GenerateCaCert("127.0.0.1", 2048)
	if err != nil {
		panic(err)
	}
	e := test.NewSslEndpoint(priv, cert)
	r := e.Start()
	defer e.Stop()
	s := NewSslStream(e.ListeningPort(), cert)
	defer s.Close()

	err = s.Send([]byte(TEST_VALUE))
	assert.Nil(t, err)
	Expect(t, r, TEST_VALUE)
}

func TestNewLineDelimitedStream_Send_ssl_should_succeed_multiple_times(t *testing.T) {
	priv, cert, err := cert.GenerateCaCert("127.0.0.1", 2048)
	if err != nil {
		panic(err)
	}
	e := test.NewSslEndpoint(priv, cert)
	r := e.Start()
	defer e.Stop()
	s := NewSslStream(e.ListeningPort(), cert)
	defer s.Close()

	for i := 0; i < 10; i++ {
		err = s.Send([]byte(TEST_VALUE))
		assert.Nil(t, err)
		Expect(t, r, TEST_VALUE)
	}
}

func TestNewLineDelimitedStream_Send_ssl_should_fail_after_disconnect(t *testing.T) {
	priv, cert, err := cert.GenerateCaCert("127.0.0.1", 2048)
	if err != nil {
		panic(err)
	}
	e := test.NewSslEndpoint(priv, cert)
	r := e.Start()
	defer e.Stop()
	s := NewSslStream(e.ListeningPort(), cert)
	defer s.Close()

	err = s.Send([]byte(TEST_VALUE))
	assert.Nil(t, err)
	Expect(t, r, TEST_VALUE)

	e.Stop()

	assert.NotNil(t, sendUntilError(s))

	err = s.Send([]byte(TEST_VALUE))
	assert.NotNil(t, err)
}

func TestNewLineDelimitedStream_Send_ssl_should_succeed_after_reconnect(t *testing.T) {
	priv, cert, err := cert.GenerateCaCert("127.0.0.1", 2048)
	if err != nil {
		panic(err)
	}
	e := test.NewSslEndpoint(priv, cert)
	r := e.Start()
	defer e.Stop()
	s := NewSslStream(e.ListeningPort(), cert)
	defer s.Close()

	err = s.Send([]byte(TEST_VALUE))
	assert.Nil(t, err)
	Expect(t, r, TEST_VALUE)

	e.Stop()
	r = e.Start()

	assert.NotNil(t, sendUntilError(s))

	err = s.Send([]byte(TEST_VALUE))
	assert.Nil(t, err)
	Expect(t, r, TEST_VALUE)
}

func TestNewLineDelimitedStream_Send_ssl_should_fail_with_bad_cert(t *testing.T) {
	priv, cert, err := cert.GenerateCaCert("127.0.0.1", 2048)
	if err != nil {
		panic(err)
	}
	e := test.NewSslEndpoint(priv, cert)
	e.Start()
	defer e.Stop()
	s := NewSslStream(e.ListeningPort(), []byte{})
	defer s.Close()

	err = s.Send([]byte(TEST_VALUE))
	assert.NotNil(t, err)
}

func TestNewLineDelimitedStream_Send_ssl_should_fail_with_plain_endpoint(t *testing.T) {
	_, cert, err := cert.GenerateCaCert("127.0.0.1", 2048)
	if err != nil {
		panic(err)
	}
	e := test.NewEndpoint()
	e.Start()
	defer e.Stop()
	s := NewSslStream(e.ListeningPort(), cert)
	defer s.Close()

	err = s.Send([]byte(TEST_VALUE))
	assert.NotNil(t, err)
}
