package main

import (
	"io"
	"net/http"
)

// ServiceHTTPClient - an interface for an HTTP client
type ServiceHTTPClient interface {
	NewRequest(method, url string, body io.Reader) (*http.Request, error)
	Do(req *http.Request) (*http.Response, error)
}

// DefaultServiceHTTPClient - for sending requests to other AeroFS services
type DefaultServiceHTTPClient struct {
	Auth   string
	Client *http.Client
}

// NewDefaultServiceHTTPClient - create a new ServiceHTTPClient to communicate with AeroFS services
func NewDefaultServiceHTTPClient(name, secret string) ServiceHTTPClient {
	return &DefaultServiceHTTPClient{
		Auth:   "Aero-Service-Shared-Secret " + name + " " + secret,
		Client: http.DefaultClient,
	}
}

// NewRequest - wrapper around http.NewRequest providing aerofs auth header
func (c *DefaultServiceHTTPClient) NewRequest(method, url string, body io.Reader) (*http.Request, error) {
	req, err := http.NewRequest(method, url, body)
	if err != nil {
		return nil, err
	}
	req.Header.Add("Authorization", c.Auth)
	return req, nil
}

// Do - wrapper around http.Client.Do
func (c *DefaultServiceHTTPClient) Do(req *http.Request) (*http.Response, error) {
	return c.Client.Do(req)
}
