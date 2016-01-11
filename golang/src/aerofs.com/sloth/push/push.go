package push

import (
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/httpClientPool"
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
)

//
// Public
//

type Notifier struct {
	AuthUser, AuthPass string // basic auth values for Button service
	Url                string // base url of Button service
	HttpClientPool     httpClientPool.Pool
}

// Notify synchronously sends a notification request
func (p *Notifier) Notify(body string, uids []string, badge int) error {
	log.Printf("push notify %v %v\n", uids, body)
	payload, err := request{Body: body, Aliases: uids, Badge: badge}.toJson()
	if err != nil {
		return err
	}
	req, err := http.NewRequest("POST", p.Url+"/notification", payload)
	if err != nil {
		return err
	}
	req.SetBasicAuth(p.AuthUser, p.AuthPass)
	req.Header.Add("Content-Type", "application/json")
	resp := <-p.HttpClientPool.Do(req)
	log.Printf("push notify response status %v\n", resp.R.StatusCode)
	return resp.Err
}

func (p *Notifier) Register(deviceType, alias, token string, dev bool) int {
	r := &buttonRegistrationRequest{
		DeviceType: deviceType,
		Alias:      alias,
		Token:      token,
		Dev:        dev,
	}
	return p.makeRegisterRequest(r)
}

//
// Private
//

type request struct {
	Aliases []string
	Body    string
	Badge   int
}

type buttonRegistrationRequest struct {
	DeviceType string
	Token      string
	Alias      string
	Dev        bool
}

func (r request) toJson() (io.Reader, error) {
	b, err := json.Marshal(r)
	if err != nil {
		return nil, err
	}
	return bytes.NewReader(b), nil
}

// Synchronously make the request and return the status code
func (p *Notifier) makeRegisterRequest(r *buttonRegistrationRequest) int {
	b, err := json.Marshal(r)
	errors.PanicOnErr(err)

	req, err := http.NewRequest("POST", p.Url+"/registration", bytes.NewReader(b))
	errors.PanicOnErr(err)
	req.SetBasicAuth(p.AuthUser, p.AuthPass)
	resp := <-p.HttpClientPool.Do(req)
	errors.PanicOnErr(resp.Err)
	return resp.R.StatusCode
}
