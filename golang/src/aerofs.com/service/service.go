package service

import (
	"bytes"
	"fmt"
	"io"
	"io/ioutil"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

// TODO: pass secret to containers through env instead of file?
func ReadDeploymentSecret() string {
	b, err := ioutil.ReadFile("/data/deployment_secret")
	if err != nil {
		panic(err)
	}
	// TODO: check for valid hex string?
	return string(bytes.TrimSpace(b))
}

func waitPort(service, port string) {
	fmt.Println("waiting for", service, port)
	for {
		_, err := net.Dial("tcp", service+":"+port)
		if err == nil {
			break
		}
		time.Sleep(time.Second)
	}
}

// Wait for all linked containers to be listening to all the ports they EXPOSE
func ServiceBarrier() {
	for _, v := range os.Environ() {
		i := strings.Index(v, ".SERVICE_PORT_")
		j := strings.Index(v, "_TCP_PORT=")
		if i == -1 || j == -1 {
			continue
		}
		service := strings.ToLower(v[:i]) + ".service"
		port := v[j+10:]
		waitPort(service, port)
	}
}

type ConfigClient interface {
	Get() (map[string]string, error)
	Set(k, v string) error
}

type HttpConfigClient struct {
	Auth   string
	Client *http.Client
}

func NewConfigClient(name string) ConfigClient {
	return &HttpConfigClient{
		Auth:   authHeader(name),
		Client: &http.Client{},
	}
}

const CONFIG_SERVICE_URL = "http://config.service:5434"

func authHeader(name string) string {
	return "Aero-Service-Shared-Secret " + name + " " + ReadDeploymentSecret()
}

func (c *HttpConfigClient) NewRequest(method, route string, body io.Reader) (*http.Request, error) {
	req, err := http.NewRequest(method, CONFIG_SERVICE_URL+route, body)
	if err != nil {
		return nil, err
	}
	req.Header.Add("Authorization", c.Auth)
	return req, nil
}

func (c *HttpConfigClient) Do(req *http.Request) (*http.Response, error) {
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

func (c *HttpConfigClient) Get() (map[string]string, error) {
	req, err := c.NewRequest("GET", "/server", nil)
	if err != nil {
		return nil, err
	}
	resp, err := c.Do(req)
	if err != nil {
		return nil, err
	}
	d, err := ioutil.ReadAll(resp.Body)
	resp.Body.Close()
	if err != nil {
		return nil, err
	}
	return ParseProperties(d), nil
}

func ParseProperties(d []byte) map[string]string {
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

func (c *HttpConfigClient) Set(k, v string) error {
	f := url.Values{}
	f.Add("key", k)
	f.Add("value", strings.Replace(v, "\n", "\\n", -1))
	req, err := c.NewRequest("POST", "/set", strings.NewReader(f.Encode()))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := c.Do(req)
	if err == nil {
		resp.Body.Close()
	}
	return err
}
