// Copyright (c) 2015, Air Computing Inc. <oss@aerofs.com>
// All rights reserved.

package main

import (
	"aerofs.com/lipwig/client"
	"aerofs.com/lipwig/server"
	"aerofs.com/lipwig/ssmp"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"net"
	"strconv"
	"testing"
	"time"
)

type test_auth struct{}

func (a *test_auth) Auth(c net.Conn, user, scheme, cred []byte) bool {
	return !ssmp.Equal(user, "reject")
}

type EventDiscarder struct{}

func (d *EventDiscarder) HandleEvent(_ client.Event) {}

type ExpectedEvents struct {
	t        *testing.T
	expected chan client.Event
}

func (q *ExpectedEvents) HandleEvent(ev client.Event) {
	select {
	case expected := <-q.expected:
		ok := assert.Equal(q.t, expected.Name, ev.Name)
		ok = assert.Equal(q.t, expected.From, ev.From) && ok
		ok = assert.Equal(q.t, expected.To, ev.To) && ok
		ok = assert.Equal(q.t, expected.Payload, ev.Payload) && ok
	case _ = <-time.After(5 * time.Second):
		assert.Fail(q.t, "unexpected event")
	}
}

type TestClient struct {
	client.Client
	h client.EventHandler
}

var ENDPOINT string

func NewServer() server.Server {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		panic(err)
	}
	s := server.NewServer(l, &test_auth{})
	ENDPOINT = "127.0.0.1:" + strconv.Itoa(s.ListeningPort())
	return s
}

func NewClientWithHandler(h client.EventHandler) TestClient {
	c, err := net.Dial("tcp", ENDPOINT)
	if err != nil {
		panic(err)
	}
	return TestClient{
		Client: client.NewClient(c, h),
		h:      h,
	}
}

func NewClient() TestClient {
	return NewClientWithHandler(&ExpectedEvents{
		expected: make(chan client.Event, 10),
	})
}

func NewLoggedInClientWithHandler(user string, h client.EventHandler) TestClient {
	c := NewClientWithHandler(h)
	r, err := c.Login(user, "none", "")
	if err != nil || r.Code != ssmp.CodeOk {
		panic("failed to login")
	}
	return c
}

func NewLoggedInClient(user string) TestClient {
	return NewLoggedInClientWithHandler(user, &ExpectedEvents{
		expected: make(chan client.Event, 10),
	})
}

func NewDiscardingLoggedInClient(user string) TestClient {
	return NewLoggedInClientWithHandler(user, &EventDiscarder{})
}

func u(hack ...interface{}) []interface{} {
	return hack
}

func expect(t *testing.T, code int, hack []interface{}) {
	require.Nil(t, hack[1])
	require.Equal(t, code, hack[0].(client.Response).Code)
}

func (c TestClient) expect(t *testing.T, expected client.Event) {
	e, ok := c.h.(*ExpectedEvents)
	if !ok {
		panic("...")
	}
	e.t = t
	e.expected <- expected
}

////////////////////////////////////////////////////////////////////////////////

func TestClient_should_accept_login(t *testing.T) {
	defer NewServer().Start().Stop()
	c := NewClient()
	defer c.Close()

	expect(t, ssmp.CodeOk, u(c.Login("foo", "none", "")))
}

func TestClient_should_reject_login(t *testing.T) {
	defer NewServer().Start().Stop()
	c := NewClient()
	defer c.Close()

	expect(t, ssmp.CodeUnauthorized, u(c.Login("reject", "none", "")))
}

func TestClient_should_fail_unicast_to_invalid(t *testing.T) {
	defer NewServer().Start().Stop()
	c := NewLoggedInClient("foo")
	defer c.Close()

	expect(t, ssmp.CodeBadRequest, u(c.Ucast("!@#$%^&*", "hello")))
}

func TestClient_should_fail_unicast_to_non_existent(t *testing.T) {
	defer NewServer().Start().Stop()
	c := NewLoggedInClient("foo")
	defer c.Close()

	expect(t, ssmp.CodeNotFound, u(c.Ucast("bar", "hello")))
}

func TestClient_should_unicast_self(t *testing.T) {
	defer NewServer().Start().Stop()
	c := NewLoggedInClient("foo")
	defer c.Close()

	c.expect(t, client.Event{
		Name:    []byte(ssmp.UCAST),
		From:    []byte("foo"),
		Payload: []byte("hello"),
	})

	expect(t, ssmp.CodeOk, u(c.Ucast("foo", "hello")))
}

func TestClient_should_unicast_other(t *testing.T) {
	defer NewServer().Start().Stop()
	foo := NewLoggedInClient("foo")
	defer foo.Close()
	bar := NewLoggedInClient("bar")
	defer bar.Close()

	bar.expect(t, client.Event{
		Name:    []byte(ssmp.UCAST),
		From:    []byte("foo"),
		Payload: []byte("hello"),
	})

	expect(t, ssmp.CodeOk, u(foo.Ucast("bar", "hello")))

	foo.expect(t, client.Event{
		Name:    []byte(ssmp.UCAST),
		From:    []byte("bar"),
		Payload: []byte("world"),
	})

	expect(t, ssmp.CodeOk, u(bar.Ucast("foo", "world")))
}

func TestClient_should_multicast(t *testing.T) {
	defer NewServer().Start().Stop()
	foo := NewLoggedInClient("foo")
	defer foo.Close()
	bar := NewLoggedInClient("bar")
	defer bar.Close()

	expect(t, ssmp.CodeOk, u(foo.Subscribe("chat")))
	expect(t, ssmp.CodeOk, u(bar.Subscribe("chat")))

	bar.expect(t, client.Event{
		Name:    []byte(ssmp.MCAST),
		From:    []byte("foo"),
		To:      []byte("chat"),
		Payload: []byte("hello"),
	})
	foo.expect(t, client.Event{
		Name:    []byte(ssmp.MCAST),
		From:    []byte("bar"),
		To:      []byte("chat"),
		Payload: []byte("world"),
	})

	expect(t, ssmp.CodeOk, u(foo.Mcast("chat", "hello")))
	expect(t, ssmp.CodeOk, u(bar.Mcast("chat", "world")))
}

func TestClient_should_get_presence(t *testing.T) {
	defer NewServer().Start().Stop()
	foo := NewLoggedInClient("foo")
	defer foo.Close()
	bar := NewLoggedInClient("bar")
	defer bar.Close()

	foo.expect(t, client.Event{
		Name: []byte(ssmp.SUBSCRIBE),
		From: []byte("bar"),
		To:   []byte("chat"),
	})

	bar.expect(t, client.Event{
		Name: []byte(ssmp.SUBSCRIBE),
		From: []byte("foo"),
		To:   []byte("chat"),
	})

	expect(t, ssmp.CodeOk, u(foo.SubscribeWithPresence("chat")))
	expect(t, ssmp.CodeOk, u(bar.SubscribeWithPresence("chat")))

	bar.expect(t, client.Event{
		Name: []byte(ssmp.UNSUBSCRIBE),
		From: []byte("foo"),
		To:   []byte("chat"),
	})

	expect(t, ssmp.CodeOk, u(foo.Unsubscribe("chat")))
}

func TestClient_should_broadcast(t *testing.T) {
	defer NewServer().Start().Stop()
	foo := NewLoggedInClient("foo")
	defer foo.Close()
	bar := NewLoggedInClient("bar")
	defer bar.Close()
	baz := NewLoggedInClient("baz")
	defer baz.Close()

	expect(t, ssmp.CodeOk, u(foo.Subscribe("foo:bar")))
	expect(t, ssmp.CodeOk, u(bar.Subscribe("foo:bar")))

	expect(t, ssmp.CodeOk, u(foo.Subscribe("foo:baz")))
	expect(t, ssmp.CodeOk, u(baz.Subscribe("foo:baz")))

	expect(t, ssmp.CodeOk, u(bar.Subscribe("bar:baz")))
	expect(t, ssmp.CodeOk, u(baz.Subscribe("bar:baz")))

	foo.expect(t, client.Event{
		Name:    []byte(ssmp.BCAST),
		From:    []byte("bar"),
		Payload: []byte("bart"),
	})
	foo.expect(t, client.Event{
		Name:    []byte(ssmp.BCAST),
		From:    []byte("baz"),
		Payload: []byte("baza"),
	})
	bar.expect(t, client.Event{
		Name:    []byte(ssmp.BCAST),
		From:    []byte("foo"),
		Payload: []byte("fool"),
	})
	bar.expect(t, client.Event{
		Name:    []byte(ssmp.BCAST),
		From:    []byte("baz"),
		Payload: []byte("baza"),
	})
	baz.expect(t, client.Event{
		Name:    []byte(ssmp.BCAST),
		From:    []byte("foo"),
		Payload: []byte("fool"),
	})
	baz.expect(t, client.Event{
		Name:    []byte(ssmp.BCAST),
		From:    []byte("bar"),
		Payload: []byte("bart"),
	})

	expect(t, ssmp.CodeOk, u(foo.Bcast("fool")))
	expect(t, ssmp.CodeOk, u(bar.Bcast("bart")))
	expect(t, ssmp.CodeOk, u(baz.Bcast("baza")))
}

func BenchmarkUCAST_self(b *testing.B) {
	defer NewServer().Start().Stop()
	foo := NewDiscardingLoggedInClient("foo")
	defer foo.Close()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		foo.Ucast("foo", "hello world")
	}
	b.StopTimer()
}

func BenchmarkMCAST_100(b *testing.B) {
	defer NewServer().Start().Stop()
	var c [100]TestClient
	for i := 0; i < len(c); i++ {
		c[i] = NewDiscardingLoggedInClient("foo" + strconv.Itoa(i))
		c[i].Subscribe("topic")
		defer c[i].Close()
	}
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		c[i%len(c)].Mcast("topic", "hello world")
	}
	b.StopTimer()
}

func BenchmarkPRESENCE_100(b *testing.B) {
	defer NewServer().Start().Stop()
	var c [100]TestClient
	for i := 0; i < len(c); i++ {
		c[i] = NewDiscardingLoggedInClient("foo" + strconv.Itoa(i))
		c[i].SubscribeWithPresence("topic")
		defer c[i].Close()
	}
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		c[i%len(c)].Unsubscribe("topic")
		c[i%len(c)].SubscribeWithPresence("topic")
	}
	b.StopTimer()

}
