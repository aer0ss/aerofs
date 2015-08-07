// Copyright (c) 2015, Air Computing Inc. <oss@aerofs.com>
// All rights reserved.

// +build aero

package main

import (
	"aerofs.com/service"
	"crypto/tls"
	"fmt"
)

func initConfig() {
	fmt.Println("waiting for deps")
	service.ServiceBarrier()
	secret = "/data/deployment_secret"
}

func tlsConfig() *tls.Config {
	return service.NewConfig("lipwig")
}
