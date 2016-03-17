package service

import (
	"bytes"
	"fmt"
	"io/ioutil"
	"net"
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
