package auth

import (
	"aerofs.com/service"
	"bytes"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"github.com/aerofs/httprouter"
	"net/http"
	"strconv"
	"strings"
	"unicode/utf8"
)

const (
	ServiceSharedSecret = "Aero-Service-Shared-Secret"
	DeviceCertificate   = "Aero-Device-Cert"
)

type AuthToken interface{}

type ServiceToken interface {
	Name() string
}

type DID [16]byte

func (d DID) String() string {
	return hex.EncodeToString(d[:])
}

type DeviceCertToken interface {
	User() string
	Device() DID
	Serial() uint64
}

type AuthTokenExtractor interface {
	Challenge() string
	Extract(params []string, r *http.Request) AuthToken
}

func concatChallenges(extractors []AuthTokenExtractor) string {
	var buf bytes.Buffer
	for i, e := range extractors {
		if i > 0 {
			buf.WriteString(", ")
		}
		buf.WriteString(e.Challenge())
		buf.WriteString(" realm=\"AeroFS\"")
	}
	return buf.String()
}

func Unauthorized(w http.ResponseWriter, challenge string) {
	w.Header().Set("WWW-Authenticate", challenge)
	http.Error(w, "Missing or invalid authorization", http.StatusUnauthorized)
}

type Context struct {
	Token  AuthToken
	Params httprouter.Params
}

type Handle func(w http.ResponseWriter, r *http.Request, c Context)

func OptionalAuth(h Handle, extractors ...AuthTokenExtractor) httprouter.Handle {
	return auth(h, false, extractors)
}

func Auth(h Handle, extractors ...AuthTokenExtractor) httprouter.Handle {
	return auth(h, true, extractors)
}

func auth(h Handle, required bool, extractors []AuthTokenExtractor) httprouter.Handle {
	challenge := concatChallenges(extractors)
	return func(w http.ResponseWriter, r *http.Request, ps httprouter.Params) {
		l := strings.Fields(r.Header.Get("Authorization"))
		if len(l) > 0 {
			for _, e := range extractors {
				if l[0] != e.Challenge() {
					continue
				}
				t := e.Extract(l[1:], r)
				if t == nil {
					fmt.Println("auth failed:", l)
					Unauthorized(w, challenge)
					return
				}
				h(w, r, Context{Token: t, Params: ps})
				return
			}
		}
		if required {
			Unauthorized(w, challenge)
		} else {
			h(w, r, Context{Params: ps})
		}
	}
}

// Device Certificate

type deviceCertToken struct {
	userid string
	did    DID
	serial uint64
}

func (t *deviceCertToken) User() string   { return t.userid }
func (t *deviceCertToken) Device() DID    { return t.did }
func (t *deviceCertToken) Serial() uint64 { return t.serial }

type deviceCertExtractor struct{}

func (e *deviceCertExtractor) Challenge() string { return DeviceCertificate }
func (e *deviceCertExtractor) Extract(params []string, r *http.Request) AuthToken {
	if len(params) != 2 || len(params[1]) != 32 {
		return nil
	}
	if r.Header.Get("Verify") != "SUCCESS" {
		return nil
	}
	userid, err := base64.StdEncoding.DecodeString(params[0])
	if err != nil || len(userid) == 0 || !utf8.Valid(userid) {
		return nil
	}
	var did DID
	n, err := hex.Decode(did[:], []byte(params[1]))
	if err != nil || n != 16 {
		return nil
	}
	serial, err := strconv.ParseUint(r.Header.Get("Serial"), 16, 64)
	if err != nil {
		return nil
	}
	if !MatchingDeviceCN(getCN(r.Header.Get("DName")), userid, did[:]) {
		return nil
	}
	return &deviceCertToken{userid: string(userid), did: did, serial: serial}
}

func getCN(dname string) string {
	i := strings.Index(dname, "CN=")
	if i == -1 {
		return ""
	}
	cn := dname[i+3:]
	j := strings.IndexByte(cn, ',')
	if j != -1 {
		return cn[:j]
	}
	return cn
}

func MatchingDeviceCN(cn string, userid, did []byte) bool {
	return matchingCN(cn, deviceCN(userid, did))
}

func deviceCN(userid, did []byte) (r []byte) {
	h := sha256.New()
	h.Write(userid)
	h.Write(did)
	return h.Sum(r)
}

func matchingCN(d string, h []byte) bool {
	if len(d) != 2*len(h) {
		return false
	}
	var acc byte
	for i := 0; i < len(h); i++ {
		acc |= h[i] ^ ((d[2*i]-'a')<<4 + (d[2*i+1] - 'a'))
	}
	return acc == 0
}

func NewDeviceCertificateExtractor() AuthTokenExtractor {
	return &deviceCertExtractor{}
}

// Service Shared Secret

type serviceToken struct {
	name string
}

func (t *serviceToken) Name() string { return t.name }

type serviceExtractor struct {
	secret []byte
}

func NewServiceSharedSecretExtractor(secret string) AuthTokenExtractor {
	return &serviceExtractor{secret: []byte(secret)}
}

func (e *serviceExtractor) Challenge() string { return ServiceSharedSecret }
func (e *serviceExtractor) Extract(params []string, r *http.Request) AuthToken {
	if len(params) == 2 && subtle.ConstantTimeCompare(e.secret, []byte(params[1])) == 1 {
		return &serviceToken{name: params[0]}
	}
	return nil
}

func AuthHeader(name string) string {
	return ServiceSharedSecret + " " + name + " " + service.ReadDeploymentSecret()
}
