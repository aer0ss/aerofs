// Copyright (c) 2015, Air Computing Inc. <oss@aerofs.com>
// All rights reserved.

// +build !aero

package main

import (
	"crypto"
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"flag"
	"fmt"
	"io/ioutil"
	"os"
)

var errInvalidCert = fmt.Errorf("invalid cert")

func certFromFile(file string) (*x509.Certificate, error) {
	d, err := ioutil.ReadFile(file)
	if err != nil {
		return nil, err
	}
	b, _ := pem.Decode(d)
	if b == nil || b.Type != "CERTIFICATE" {
		return nil, errInvalidCert
	}
	return x509.ParseCertificate(b.Bytes)
}

// NB: uses global variables initialized from command line flags
func tlsConfig() *tls.Config {
	if len(hostname) == 0 || len(cacertFile) == 0 ||
		len(certFile) == 0 || len(keyFile) == 0 {
		flag.Usage()
		os.Exit(1)
	}
	cacert, err := certFromFile(cacertFile)
	if err != nil {
		fmt.Println(err)
		os.Exit(1)
	}
	cert, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		fmt.Println(err)
		os.Exit(1)
	}
	x509, err := x509.ParseCertificate(cert.Certificate[0])
	if err != nil {
		fmt.Println(err)
		os.Exit(1)
	}

	return NewTLSConfig(hostname, cert.PrivateKey, x509, cacert)
}

func NewTLSConfig(host string, key crypto.PrivateKey, cert *x509.Certificate, cacert *x509.Certificate) *tls.Config {
	roots := x509.NewCertPool()
	roots.AddCert(cacert)
	// lock down TLS config to 1.2 or higher and safest available ciphersuites
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
