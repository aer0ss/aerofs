package main

import (
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
)

// ServiceHTTPClient - for sending requests to other AeroFS services
type ServiceHTTPClient struct {
	Auth   string
	Client *http.Client
}

// NewServiceHTTPClient - create a new ServiceHTTPClient to communicate with AeroFS services
func NewServiceHTTPClient(name, secret string) *ServiceHTTPClient {
	return &ServiceHTTPClient{
		Auth:   "Aero-Service-Shared-Secret " + name + " " + secret,
		Client: http.DefaultClient,
	}
}

// NewRequest - wrapper around http.NewRequest providing aerofs auth header
func (c *ServiceHTTPClient) NewRequest(method, url string, body io.Reader) (*http.Request, error) {
	req, err := http.NewRequest(method, url, body)
	if err != nil {
		return nil, err
	}
	req.Header.Add("Authorization", c.Auth)
	return req, nil
}

// Do - wrapper around http.Client.Do, returns error on non-200 status codes
func (c *ServiceHTTPClient) Do(req *http.Request) (*http.Response, error) {
	resp, err := c.Client.Do(req)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != 200 {
		t, _ := ioutil.ReadAll(resp.Body)
		resp.Body.Close()
		return nil, fmt.Errorf("unexpected status code: %s\n%s", resp.Status, t)
	}
	return resp, nil
}
