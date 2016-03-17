// Copyright (c) 2015, Air Computing Inc. <oss@aerofs.com>
// All rights reserved.

// +build aero

package cfg

import (
	"aerofs.com/service"
	"aerofs.com/service/aerotls"
	"crypto/tls"
	"fmt"
)

var Secret string = "/data/deployment_secret"

func InitConfig() {
	fmt.Println("waiting for deps")
	service.ServiceBarrier()
}

func TLSConfig() *tls.Config {
	return aerotls.NewConfig("lipwig")
}
