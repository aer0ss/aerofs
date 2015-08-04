// +build aero

package main

import (
	"aerofs.com/service"
	"crypto/tls"
	"fmt"
)

func tlsConfig() *tls.Config {
	fmt.Println("waiting for deps")
	service.ServiceBarrier()

	secret = "/data/deployment_secret"
	return service.NewConfig("lipwig")
}
