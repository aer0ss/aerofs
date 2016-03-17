// package config provides access to appliance configuration values
package config

import (
	"aerofs.com/service"
	"bytes"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/url"
	"strings"
)

const CONFIG_SERVICE_URL = "http://config.service:5434"

type Client interface {
	Get() (map[string]string, error)
	Set(k, v string) error
}

type httpClient struct {
	Auth   string
	Client *http.Client
}

func NewClient(name string) Client {
	return &httpClient{
		Auth:   authHeader(name),
		Client: &http.Client{},
	}
}

func authHeader(name string) string {
	return "Aero-Service-Shared-Secret " + name + " " + service.ReadDeploymentSecret()
}

func (c *httpClient) Get() (map[string]string, error) {
	req, err := c.newRequest("GET", "/server", nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.do(req)
	if err != nil {
		return nil, err
	}
	d, err := ioutil.ReadAll(resp.Body)
	resp.Body.Close()
	if err != nil {
		return nil, err
	}
	return parseProperties(d), nil
}

func (c *httpClient) Set(k, v string) error {
	f := url.Values{}
	f.Add("key", k)
	f.Add("value", strings.Replace(v, "\n", "\\n", -1))
	req, err := c.newRequest("POST", "/set", strings.NewReader(f.Encode()))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := c.do(req)
	if err == nil {
		resp.Body.Close()
	}
	return err
}

func (c *httpClient) newRequest(method, route string, body io.Reader) (*http.Request, error) {
	req, err := http.NewRequest(method, CONFIG_SERVICE_URL+route, body)
	if err != nil {
		return nil, err
	}
	req.Header.Add("Authorization", c.Auth)
	return req, nil
}

func (c *httpClient) do(req *http.Request) (*http.Response, error) {
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

func parseProperties(d []byte) map[string]string {
	lines := bytes.Split(bytes.Replace(d, []byte{'\\', '\n'}, []byte{'\\', 'n'}, -1), []byte{'\n'})
	m := make(map[string]string)
	for _, l := range lines {
		l = bytes.TrimLeft(l, " \t")
		if len(l) == 0 || bytes.HasPrefix(l, []byte{'#'}) {
			continue
		}
		i := bytes.Index(l, []byte{'='})
		if i == -1 {
			continue
		}
		k := string(l[:i])
		v := ""
		if i+1 < len(l) {
			v = string(l[i+1:])
		}
		m[k] = strings.Replace(v, "\\n", "\n", -1)
	}
	return m
}
