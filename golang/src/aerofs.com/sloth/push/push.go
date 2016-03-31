package push

import (
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/httpClientPool"
	. "aerofs.com/sloth/structs"
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
)

type Notifier interface {

	// Register synchronously sends a registration request and returns the http
	// status code of the response
	Register(deviceType, alias, token string, dev bool) int

	// Notify synchronously sends a notification request
	Notify(body string, uids []string, badge int) error

	// NotifyNewMessage should call Notify with an appropriate body and badge
	// count when "caller" has messaged "targets".
	NotifyNewMessage(caller *User, targets []string)
}

func NewNotifier(user, pass, url string, poolSize uint) Notifier {
	return &notifier{
		AuthUser:       user,
		AuthPass:       pass,
		Url:            url,
		HttpClientPool: httpClientPool.NewPool(poolSize),
	}
}

type notifier struct {
	AuthUser, AuthPass string // basic auth values for Button service
	Url                string // base url of Button service
	HttpClientPool     httpClientPool.Pool
}

// Notify synchronously sends a notification request
func (p *notifier) Notify(body string, uids []string, badge int) error {
	if len(uids) == 0 {
		return nil
	}
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
	if resp.Err == nil {
		log.Printf("push notify response status %v\n", resp.R.StatusCode)
	} else {
		log.Printf("push notify err %v\n", resp.Err)
	}
	return resp.Err
}

func (p *notifier) Register(deviceType, alias, token string, dev bool) int {
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
func (p *notifier) makeRegisterRequest(r *buttonRegistrationRequest) int {
	b, err := json.Marshal(r)
	errors.PanicOnErr(err)

	req, err := http.NewRequest("POST", p.Url+"/registration", bytes.NewReader(b))
	errors.PanicOnErr(err)
	req.SetBasicAuth(p.AuthUser, p.AuthPass)
	resp := <-p.HttpClientPool.Do(req)
	errors.PanicOnErr(resp.Err)
	return resp.R.StatusCode
}
