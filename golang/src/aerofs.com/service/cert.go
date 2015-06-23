package service

import (
	"bytes"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"
	"path"
	"time"
)

func SetupCert(cn, file string) (*rsa.PrivateKey, *x509.Certificate, error) {
	priv, cert, err := LoadCert(file)
	if err != nil {
		fmt.Println("generating fresh key/cert")
		priv, cert, err = GenerateCert(cn)
		if err != nil {
			return nil, nil, err
		}
		if err = SaveCert(priv, cert, file); err != nil {
			return nil, nil, err
		}
	} else {
		fmt.Println("loaded key/cert")
	}
	if ShouldRecertify(cn, cert) {
		fmt.Println("requesting fresh cert with same private key")
		if cert, err = Certify(priv, cn); err != nil {
			return nil, nil, err
		}
		if err = SaveCert(priv, cert, file); err != nil {
			return nil, nil, err
		}
	}
	return priv, cert, nil
}

// automatically re-certify if
//  - CN changed
//  - cert is halfway through its lifespan
func ShouldRecertify(cn string, cert *x509.Certificate) bool {
	return cert.Subject.CommonName != cn ||
		cert.NotAfter.Sub(time.Now()) < cert.NotAfter.Sub(cert.NotBefore)/2
}

func LoadCert(file string) (*rsa.PrivateKey, *x509.Certificate, error) {
	d, err := ioutil.ReadFile(file)
	if err != nil {
		return nil, nil, err
	}
	var b *pem.Block
	var priv *rsa.PrivateKey
	var cert *x509.Certificate
	for len(d) > 0 {
		b, d = pem.Decode(d)
		if b == nil {
			break
		}
		fmt.Println(b.Type)
		if b.Type == "RSA PRIVATE KEY" {
			if priv != nil {
				return nil, nil, fmt.Errorf("multiple priv key")
			}
			priv, err = x509.ParsePKCS1PrivateKey(b.Bytes)
		} else if b.Type == "PRIVATE KEY" {
			if priv != nil {
				return nil, nil, fmt.Errorf("multiple priv key")
			}
			key, err := x509.ParsePKCS8PrivateKey(b.Bytes)
			if err != nil {
				return nil, nil, err
			}
			var ok bool
			if priv, ok = key.(*rsa.PrivateKey); !ok {
				return nil, nil, fmt.Errorf("invalid key type")
			}
		} else if b.Type == "CERTIFICATE" {
			if cert != nil {
				return nil, nil, fmt.Errorf("multiple cert")
			}
			cert, err = x509.ParseCertificate(b.Bytes)
		}
		if err != nil {
			return nil, nil, err
		}
	}
	if priv == nil || cert == nil {
		return nil, nil, fmt.Errorf("invalid stored key/cert")
	}
	return priv, cert, nil
}

func SaveCert(priv *rsa.PrivateKey, cert *x509.Certificate, file string) error {
	err := os.MkdirAll(path.Dir(file), 0600)
	if err != nil {
		return err
	}
	f, err := os.OpenFile(file, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0600)
	if err != nil {
		return err
	}
	defer f.Close()
	err = pem.Encode(f, &pem.Block{
		Type:  "RSA PRIVATE KEY",
		Bytes: x509.MarshalPKCS1PrivateKey(priv),
	})
	pem.Encode(f, &pem.Block{
		Type:  "CERTIFICATE",
		Bytes: cert.Raw,
	})
	return nil
}

func GenerateCert(cn string) (*rsa.PrivateKey, *x509.Certificate, error) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return nil, nil, err
	}
	cert, err := Certify(priv, cn)
	if err != nil {
		return nil, nil, err
	}
	return priv, cert, nil
}

func Certify(priv *rsa.PrivateKey, cn string) (*x509.Certificate, error) {
	csr, err := x509.CreateCertificateRequest(rand.Reader, &x509.CertificateRequest{
		Subject: pkix.Name{
			Country:      []string{"US"},
			Province:     []string{"CA"},
			Locality:     []string{"SF"},
			Organization: []string{"aerofs.com"},
			CommonName:   cn,
		},
	}, priv)

	if err != nil {
		return nil, err
	}

	body := bytes.NewReader(pem.EncodeToMemory(&pem.Block{
		Type:  "CERTIFICATE REQUEST",
		Bytes: csr,
	}))

	req, err := http.NewRequest("POST", "http://ca.service:9002/prod", body)
	if err != nil {
		return nil, err
	}
	req.Header.Add("Authorization", authHeader("crt-create"))
	client := &http.Client{}
	resp, err := client.Do(req)
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("Unexpected response from ca: %d", resp.StatusCode)
	}
	d, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("")
	}
	b, _ := pem.Decode(d)
	if b == nil || b.Type != "CERTIFICATE" {
		return nil, fmt.Errorf("")
	}
	return x509.ParseCertificate(b.Bytes)
}
