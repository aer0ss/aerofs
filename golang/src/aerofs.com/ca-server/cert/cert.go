package cert

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"math/big"
	"net"
	"time"
)

import mrand "math/rand"

func genKeyId(pub interface{}) ([]byte, error) {
	der, err := x509.MarshalPKIXPublicKey(pub)
	if err != nil {
		return nil, err
	}
	h := sha1.Sum(der)
	return h[:], nil
}

func GenerateCaCert(cn string, bits int) (*rsa.PrivateKey, []byte, error) {
	key, err := rsa.GenerateKey(rand.Reader, bits)
	if err != nil {
		return nil, nil, err
	}
	der, err := NewCaCert(key, cn)
	if err != nil {
		return nil, nil, err
	}
	return key, der, nil
}

func NewUUID() string {
	uuid := make([]byte, 16)
	_, err := rand.Read(uuid)
	if err != nil {
		panic(err)
	}
	return hex.EncodeToString(uuid)
}

func NewCaCert(key *rsa.PrivateKey, cn string) ([]byte, error) {
	keyId, err := genKeyId(key.Public())
	if err != nil {
		return nil, err
	}
	template := &x509.Certificate{
		SerialNumber: big.NewInt(mrand.Int63()),
		Subject: pkix.Name{
			Country:      []string{"US"},
			Province:     []string{"California"},
			Locality:     []string{"San Francisco"},
			Organization: []string{"aerofs.com"},
			CommonName:   cn,
		},
		NotBefore:             time.Now().AddDate(0, 0, -1),
		NotAfter:              time.Now().AddDate(10, 0, 0),
		KeyUsage:              x509.KeyUsageCRLSign | x509.KeyUsageCertSign,
		BasicConstraintsValid: true,
		IsCA:               true,
		SubjectKeyId:       keyId,
		AuthorityKeyId:     keyId,
		SignatureAlgorithm: x509.SHA256WithRSA,
	}
	if ip := net.ParseIP(cn); ip != nil {
		template.IPAddresses = append(template.IPAddresses, ip)
	}
	return x509.CreateCertificate(rand.Reader, template, template, key.Public(), key)
}

type CertSigner struct {
	Priv    *rsa.PrivateKey
	Cert    *x509.Certificate
	CertDER []byte
}

func NewSigner(key *rsa.PrivateKey, der []byte) *CertSigner {
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		panic(err)
	}
	return &CertSigner{
		Priv:    key,
		Cert:    cert,
		CertDER: der,
	}
}

func (signer *CertSigner) SignCSR(csr *x509.CertificateRequest, serial int64) ([]byte, error) {
	subjectKeyId, err := genKeyId(csr.PublicKey)
	if err != nil {
		return nil, err
	}
	authorityKeyId, err := genKeyId(signer.Priv.Public())
	if err != nil {
		return nil, err
	}
	template := &x509.Certificate{
		SerialNumber:          big.NewInt(serial),
		Subject:               csr.Subject,
		NotBefore:             time.Now().AddDate(0, 0, -1),
		NotAfter:              time.Now().AddDate(1, 0, 0),
		BasicConstraintsValid: false,
		IsCA:               false,
		DNSNames:           csr.DNSNames,
		EmailAddresses:     csr.EmailAddresses,
		IPAddresses:        csr.IPAddresses,
		ExtKeyUsage:        []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth, x509.ExtKeyUsageClientAuth},
		SubjectKeyId:       subjectKeyId,
		AuthorityKeyId:     authorityKeyId,
		SignatureAlgorithm: x509.SHA256WithRSA,
	}
	// add altname for ip
	for _, rdn := range csr.Subject.ToRDNSequence() {
		for _, at := range rdn {
			// ignore everything but CN
			if !at.Type.Equal([]int{2, 5, 4, 3}) {
				continue
			}
			if ip := net.ParseIP(at.Value.(string)); ip != nil {
				template.IPAddresses = append(template.IPAddresses, ip)
			}
		}
	}
	return x509.CreateCertificate(rand.Reader, template, signer.Cert, csr.PublicKey, signer.Priv)
}

func WritePEM(der []byte, w io.Writer) {
	pem.Encode(w, &pem.Block{
		Type:  "CERTIFICATE",
		Bytes: der,
	})
}

func DecodeCSR(r io.Reader) (*x509.CertificateRequest, error) {
	body, err := ioutil.ReadAll(r)
	if err != nil {
		return nil, err
	}
	b, _ := pem.Decode(body)
	if b == nil {
		return nil, errors.New("No PEM data")
	}
	if b.Type != "CERTIFICATE REQUEST" {
		return nil, fmt.Errorf("Not a certificate request")
	}
	return x509.ParseCertificateRequest(b.Bytes)
}
