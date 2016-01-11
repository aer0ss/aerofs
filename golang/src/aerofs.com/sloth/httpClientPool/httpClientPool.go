// Package httpClientPool implements a thread-safe fixed-size pool of http
// clients that can be reused to save TCP and TLS overhead.
package httpClientPool

import (
	"io/ioutil"
	"net/http"
)

// Response contains the results of a call to http.Client.Do(), untouched
// except that the body of the http.Response has been read and closed. Callers
// should use the body field of this struct instead.
type Response struct {
	R    *http.Response
	Body []byte
	Err  error
}

// Pool defines a single method which makes a request and returns a
// promise-like chan of Response
type Pool interface {
	// Do is a non-blocking request function which returns a chan through which
	// a response will be passed when the HTTP call completes
	Do(*http.Request) <-chan *Response
}

// NewPool constructs a Pool of "size" http clients
func NewPool(size uint) Pool {
	pool := &pool{
		clients: make(chan *http.Client, size),
	}
	for i := uint(0); i < size; i += 1 {
		pool.clients <- &http.Client{}
	}
	return pool
}

// pool implements the Pool interface
type pool struct {
	clients chan *http.Client
}

func (p *pool) Do(r *http.Request) <-chan *Response {
	responseChan := make(chan *Response)

	go func() {
		// blocking call to get a free client
		client := <-p.clients
		defer func() {
			p.clients <- client
		}()

		resp, err := client.Do(r)
		defer resp.Body.Close()
		if err != nil {
			responseChan <- &Response{R: resp, Err: err}
			return
		}
		body, err := ioutil.ReadAll(r.Body)
		if err != nil {
			responseChan <- &Response{R: resp, Err: err}
			return
		}
		responseChan <- &Response{
			R:    resp,
			Body: body,
			Err:  nil,
		}
	}()

	return responseChan
}
