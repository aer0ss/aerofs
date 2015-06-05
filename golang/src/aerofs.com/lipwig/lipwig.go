package main

import (
	"aerofs.com/lipwig/server"
	"aerofs.com/service"
	"fmt"
)

func main() {
	fmt.Println("waiting for deps")
	service.ServiceBarrier()

	tls := service.NewConfig("lipwig")
	auth := &server.MultiSchemeAuthenticator{
		Schemes: map[string]server.AuthenticatorFunc{
			"cert":   server.CertAuth,
			"secret": server.SecretAuth([]byte(service.ReadDeploymentSecret())),
		},
	}
	l, err := service.Listen(8787, tls)
	if err != nil {
		panic(err)
	}
	s := server.NewServer(l, auth)
	fmt.Println("lipwig serving at", s.ListeningPort())
	err = s.Serve()
	if err != nil {
		panic(err)
	}
	fmt.Println("exit.")
}
