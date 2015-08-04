package main

import (
	"aerofs.com/lipwig/server"
	"bytes"
	"crypto/tls"
	"flag"
	"fmt"
	"io/ioutil"
	"net"
)

var hostname string
var certFile string
var keyFile string
var cacertFile string

var secret string

func main() {
	var address string

	flag.StringVar(&address, "listen", "0.0.0.0:8787", "listening address")
	flag.StringVar(&secret, "secret", "", "file from which to read shared secret for authentication")
	flag.StringVar(&hostname, "host", "", "TLS hostname")
	flag.StringVar(&cacertFile, "cacert", "", "CA certificate for client authentication")
	flag.StringVar(&certFile, "cert", "", "Certificate for server authentication, must be signed by CA certificate")
	flag.StringVar(&keyFile, "key", "", "Private key for server authentication")
	flag.Parse()

	cfg := tlsConfig()
	auth := &server.MultiSchemeAuthenticator{
		Schemes: map[string]server.AuthenticatorFunc{
			"cert": server.CertAuth,
		},
	}

	if len(secret) > 0 {
		b, err := ioutil.ReadFile(secret)
		if err != nil {
			panic(err)
		}
		auth.Schemes["secret"] = server.SecretAuth(bytes.TrimSpace(b))
	}

	l, err := net.Listen("tcp", address)
	if err != nil {
		panic(err)
	}
	s := server.NewServer(tls.NewListener(l, cfg), auth)
	fmt.Println("lipwig serving at", s.ListeningPort())
	err = s.Serve()
	if err != nil {
		panic(err)
	}
	fmt.Println("exit.")
}
