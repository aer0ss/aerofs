package main

import (
	"aerofs.com/ca-server/cert"
	"aerofs.com/service"
	"aerofs.com/service/mysql"
	"bytes"
	"crypto/rand"
	"crypto/rsa"
	"crypto/subtle"
	"crypto/x509"
	"database/sql"
	"errors"
	"fmt"
	"github.com/aerofs/httprouter"
	"net/http"
	"strings"
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
			key, err = rsa.GenerateKey(rand.Reader, 2048)
			if err != nil {
				return err
			}
			der, err = cert.NewCaCert(key)
			if err != nil {
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

func ServiceAuth(h httprouter.Handle, secret string) httprouter.Handle {
	return func(w http.ResponseWriter, r *http.Request, ps httprouter.Params) {
		l := strings.Fields(r.Header.Get("Authorization"))
		if len(l) == 3 &&
			l[0] == "Aero-Service-Shared-Secret" &&
			subtle.ConstantTimeCompare([]byte(secret), []byte(l[2])) == 0 {
			h(w, r, ps)
		} else {
			w.Header().Set("WWW-Authenticate", "Aero-Service-Shared-Secret realm=AeroFS")
			http.Error(w, "Missing or invalid authorization", http.StatusUnauthorized)
		}
	}
}

func writeError(w http.ResponseWriter, msg string, status int) {
	fmt.Println(" > " + string(status) + " : " + msg)
	http.Error(w, msg, status)
}

func csrHandler(db *sql.DB, signer *cert.CertSigner, w http.ResponseWriter, r *http.Request) {
	fmt.Println(r.Method + " " + r.RequestURI)

	csr, err := cert.DecodeCSR(r.Body)
	if err != nil {
		writeError(w, err.Error(), 400)
		return
	}
	serial, err := acquireSerial(db)
	if err != nil {
		writeError(w, "failed to acquire serial number ["+err.Error()+"]", 500)
		return
	}
	der, err := signer.SignCSR(csr, serial)
	if err != nil {
		writeError(w, "failed to sign CSR ["+err.Error()+"]", 500)
		return
	}
	err = setCertificate(db, serial, der)
	if err != nil {
		writeError(w, "failed to sign CSR ["+err.Error()+"]", 500)
		return
	}
	fmt.Println(" > signed")
	w.WriteHeader(200)
	cert.WritePEM(der, w)
}

func cacertHandler(signer *cert.CertSigner, w http.ResponseWriter, r *http.Request) {
	fmt.Println(r.Method + " " + r.RequestURI)
	w.WriteHeader(http.StatusOK)
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

	fmt.Println("initializing db")
	db := mysql.CreateConnection(mysql.UrlFromConfig(c), "aerofs_ca")

	fmt.Println("initializing cacert")
	signer := loadCaCert(db)

	fmt.Println("updating config")
	buf := &bytes.Buffer{}
	cert.WritePEM(signer.CertDER, buf)
	config.Set("base_ca_cert", string(buf.Bytes()))

	secret := service.ReadDeploymentSecret()

	router := httprouter.New()
	router.GET("/prod", ServiceAuth(func(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
		csrHandler(db, signer, w, r)
	}, secret))
	router.GET("/prod/cacert.pem", func(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
		cacertHandler(signer, w, r)
	})

	fmt.Println("ca-server serving at 9002")
	err = http.ListenAndServe(":9002", router)
	if err != nil {
		panic("failed: " + err.Error())
	}
}
