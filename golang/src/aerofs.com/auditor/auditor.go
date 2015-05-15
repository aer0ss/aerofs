package main

import (
	"aerofs.com/service"
	"aerofs.com/service/auth"
	"encoding/json"
	"fmt"
	"github.com/aerofs/httprouter"
	"io/ioutil"
	"net/http"
)

// TODO avoid global state...
var s Downstream

func SendDownstream(w http.ResponseWriter, d []byte) {
	err := s.Send(d)
	if err == nil {
		http.Error(w, "", http.StatusOK)
	} else {
		http.Error(w, "Downstream unavailable", http.StatusServiceUnavailable)
	}
}

func Event(w http.ResponseWriter, r *http.Request, c auth.Context) {
	d, err := ioutil.ReadAll(r.Body)
	if err != nil {
		http.Error(w, "Invalid event", http.StatusBadRequest)
		return
	}
	var e map[string]interface{}
	err = json.Unmarshal(d, &e)
	if err != nil {
		http.Error(w, "Invalid event", http.StatusBadRequest)
		return
	}
	if e["event"] == nil || e["timestamp"] == nil || e["topic"] == nil {
		http.Error(w, "Request missing required parameters", http.StatusBadRequest)
		return
	}
	ud, ok := c.Token.(auth.DeviceCertToken)
	if ok {
		e["verified_submitter"] = map[string]string{
			"user_id":   ud.User(),
			"device_id": ud.Device().String(),
		}
	}
	d, err = json.Marshal(e)
	if err != nil {
		http.Error(w, "", http.StatusInternalServerError)
		return
	}
	SendDownstream(w, d)
}

func main() {
	fmt.Println("waiting for deps")
	service.ServiceBarrier()

	config := service.NewConfigClient("auditor")
	c, err := config.Get()
	if err != nil {
		panic(err)
	}

	s = NewDownstream(c)

	secret := service.ReadDeploymentSecret()

	router := httprouter.New()
	router.POST("/event", auth.Auth(Event,
		auth.NewDeviceCertificateExtractor(),
		auth.NewServiceSharedSecretExtractor(secret),
	))

	fmt.Println("auditor serving at 9300")
	err = http.ListenAndServe(":9300", service.Log(router))
	if err != nil {
		panic("failed: " + err.Error())
	}
}
