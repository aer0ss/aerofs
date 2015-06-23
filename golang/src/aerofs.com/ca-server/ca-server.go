package main

import (
	"aerofs.com/ca-server/cert"
	"aerofs.com/service"
	"aerofs.com/service/auth"
	"aerofs.com/service/mysql"
	"bytes"
	"crypto/rsa"
	"crypto/x509"
	"database/sql"
	"errors"
	"fmt"
	"github.com/aerofs/httprouter"
	"net"
	"net/http"
	"strings"
	"time"
)

import mrand "math/rand"

func loadCaCert(db *sql.DB) *cert.CertSigner {
	var key *rsa.PrivateKey
	var der []byte
	err := mysql.Transact(db, func(tx *sql.Tx) error {
		row := tx.QueryRow("select ca_key, ca_cert from server_configuration")
		var priv []byte
		err := row.Scan(&priv, &der)
		switch {
		case err == sql.ErrNoRows:
			fmt.Println("creating new key/cert")
			cn := "AeroFS-" + cert.NewUUID()
			if key, der, err = cert.GenerateCaCert(cn, 2048); err != nil {
				return err
			}
			_, err = tx.Exec("insert into server_configuration(ca_key, ca_cert) values(?, ?)",
				x509.MarshalPKCS1PrivateKey(key),
				der,
			)
			return err
		case err != nil:
			return err
		default:
			fmt.Println("loading key/cert from db")
			key, err = x509.ParsePKCS1PrivateKey(priv)
			return err
		}
	})
	if err != nil {
		panic(err)
	}
	return cert.NewSigner(key, der)
}

func acquireSerial(db *sql.DB) (int64, error) {
	for i := 0; i < 10; i++ {
		serial := mrand.Int63()
		err := tryAcquireSerial(db, serial)
		if err == nil {
			return serial, nil
		}
		fmt.Println("serial", serial, "could not be acquired", err)
	}
	return -1, errors.New("could not find a free serial number")
}

func tryAcquireSerial(db *sql.DB, serial int64) error {
	return mysql.Transact(db, func(tx *sql.Tx) error {
		_, err := tx.Exec("insert into signed_certificates(serial_number, certificate) values (?, '')", serial)
		return err
	})
}

func setCertificate(db *sql.DB, serial int64, cert []byte) error {
	return mysql.Transact(db, func(tx *sql.Tx) error {
		_, err := tx.Exec("update signed_certificates set certificate = ? where serial_number = ?", cert, serial)
		return err
	})
}

func WriteError(w http.ResponseWriter, msg string, status int) {
	fmt.Println("error:", msg)
	http.Error(w, msg, status)
}

func csrHandler(db *sql.DB, signer *cert.CertSigner, w http.ResponseWriter, r *http.Request) {
	csr, err := cert.DecodeCSR(r.Body)
	if err != nil {
		WriteError(w, err.Error(), 400)
		return
	}
	altNames := r.URL.Query()["alt"]
	fmt.Println("csr for ", csr.Subject, altNames)
	for _, altName := range altNames {
		if ip := net.ParseIP(altName); ip != nil {
			csr.IPAddresses = append(csr.IPAddresses, ip)
		} else if strings.Contains(altName, "@") {
			csr.EmailAddresses = append(csr.EmailAddresses, altName)
		} else {
			csr.DNSNames = append(csr.DNSNames, altName)
		}
	}

	serial, err := acquireSerial(db)
	if err != nil {
		WriteError(w, "failed to acquire serial number ["+err.Error()+"]", 500)
		return
	}
	der, err := signer.SignCSR(csr, serial)
	if err != nil {
		WriteError(w, "failed to sign CSR ["+err.Error()+"]", 500)
		return
	}
	err = setCertificate(db, serial, der)
	if err != nil {
		WriteError(w, "failed to sign CSR ["+err.Error()+"]", 500)
		return
	}
	cert.WritePEM(der, w)
}

func cacertHandler(signer *cert.CertSigner, w http.ResponseWriter, r *http.Request) {
	cert.WritePEM(signer.CertDER, w)
}

func main() {
	fmt.Println("waiting for deps")
	service.ServiceBarrier()

	config := service.NewConfigClient("ca-server")
	c, err := config.Get()
	if err != nil {
		panic(err)
	}

	mrand.Seed(time.Now().UTC().UnixNano())

	fmt.Println("initializing db")
	db := mysql.CreateConnection(mysql.UrlFromConfig(c), "aerofs_ca")

	fmt.Println("initializing cacert")
	signer := loadCaCert(db)

	fmt.Println("updating config")
	buf := &bytes.Buffer{}
	cert.WritePEM(signer.CertDER, buf)
	err = config.Set("base_ca_cert", string(buf.Bytes()))
	if err != nil {
		panic(err)
	}

	secret := service.ReadDeploymentSecret()

	router := httprouter.New()
	router.POST("/prod", auth.Auth(func(w http.ResponseWriter, r *http.Request, _ auth.Context) {
		csrHandler(db, signer, w, r)
	}, auth.NewServiceSharedSecretExtractor(secret)))
	router.GET("/prod/cacert.pem", func(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
		cacertHandler(signer, w, r)
	})

	fmt.Println("ca-server serving at 9002")
	err = http.ListenAndServe(":9002", service.Log(router))
	if err != nil {
		panic("failed: " + err.Error())
	}
}
