package service

import (
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"net"
	"strconv"
)

func NewConfig(name string) *tls.Config {
	config := NewConfigClient(name)
	c, err := config.Get()
	if err != nil {
		panic(err)
	}

	host := c["base.host.unified"]
	b, _ := pem.Decode([]byte(c["config.loader.base_ca_certificate"]))
	if b == nil || b.Type != "CERTIFICATE" {
		panic("invalid cacert")
	}

	cacert, err := x509.ParseCertificate(b.Bytes)
	if err != nil {
		panic(err)
	}

	key, cert, err := SetupCert(host, "/data/"+name+"/cert.pem")
	if err != nil {
		panic(err)
	}

	return NewTLSConfig(host, key, cert, cacert)
}

func NewTLSConfig(host string, key *rsa.PrivateKey, cert *x509.Certificate, cacert *x509.Certificate) *tls.Config {
	roots := x509.NewCertPool()
	roots.AddCert(cacert)
	// lock down TLS config to 1.2 or higher and safest available ciphersuites
	// NB: this list of ciphersuite does NOT match the one enforced in Java land
	// because go support fewer ciphersuites...
	// In particular it doesn't support any non-EC DHE ciphers:
	// https://github.com/golang/go/issues/7758
	return &tls.Config{
		ServerName: host,
		MinVersion: tls.VersionTLS12,
		CipherSuites: []uint16{
			tls.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
			tls.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
			tls.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
			tls.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
			tls.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
			tls.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
		},
		Certificates: []tls.Certificate{
			tls.Certificate{
				Certificate: [][]byte{
					cert.Raw,
					cacert.Raw,
				},
				PrivateKey: key,
				Leaf:       cert,
			},
		},
		ClientAuth: tls.VerifyClientCertIfGiven,
		ClientCAs:  roots,
	}
}

func Listen(port int, cfg *tls.Config) (net.Listener, error) {
	l, err := net.Listen("tcp", "0.0.0.0:"+strconv.Itoa(port))
	if err != nil {
		return nil, err
	}
	if cfg != nil {
		l = tls.NewListener(l, cfg)
	}
	return l, nil
}
