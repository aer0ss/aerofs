// Trifrost does two things:
//
// 1. Sends a verification code to an email address
// 2. Trades verification codes for OAuth tokens
//
//
// Thing #1 is accomplished with:
//
// curl -XPOST /auth_code -d'{"email": "jonathan@aerofs.com"}'
//
//
// Thing #2 is accomplished with:
//
// curl -XPOST /token -d'{"email": "jonathan@aerofs.com", "auth_code": 123456}'
//
// Returns {"access_token": "a1b2c3..."}
package main

import (
	"aerofs.com/service"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"fmt"
	"github.com/aerofs/httprouter"
	"log"
	"net/http"
	"net/smtp"
)

const (
	// default port on which to listen
	PORT = 8888

	// buffer size for email chan
	MAIL_QUEUE_SIZE = 1000

	// OAuth Client Id for trifrost
	OAUTH_CLIENT_ID = "aerofs-trifrost"
)

// JSON request body for POST /auth_code
type authCodeRequest struct {
	Email string `json:"email"`
}

// JSON request body for POST /token
type tokenRequest struct {
	Email    string `json:"email"`
	AuthCode int    `json:"auth_code"`
}

// JSON response body for POST /token
type tokenResponse struct {
	AccessToken string `json:"access_token"`
}

// Pretty self-explanatory
type EmailCodePair struct {
	Email string
	Code  int
}

// context contains data required by the handlers
type context struct {

	// stores (email, auth code) pairs
	codeMap CodeMap

	// emails are sent for each value passed through this chan
	mail chan<- EmailCodePair

	// call tokenCreator#CreateToken to create access tokens
	tokenCreator *TokenCreator
}

// handleAuthCode handles calls to /auth_code
//
// It generates a six-digit auth code (one outstanding per email) and emails it
// to the given email address.
//
// TODO: rate limit this
func (ctx *context) handleAuthCode(w http.ResponseWriter, req *http.Request, _ httprouter.Params) {
	log.Println("handle auth code")
	defer recoverAndThrow500(w)

	var body authCodeRequest
	if err := json.NewDecoder(req.Body).Decode(&body); err != nil {
		w.WriteHeader(400)
		log.Print(err)
		return
	}
	log.Print("getting auth code for ", body.Email)

	authCode := ctx.codeMap.GetCode(body.Email)
	log.Printf("got code %v for %v\n", authCode, body.Email)

	ctx.mail <- EmailCodePair{
		Email: body.Email,
		Code:  authCode,
	}
}

func (ctx *context) handleToken(w http.ResponseWriter, req *http.Request, _ httprouter.Params) {
	defer recoverAndThrow500(w)

	var body tokenRequest
	if err := json.NewDecoder(req.Body).Decode(&body); err != nil {
		w.WriteHeader(400)
		log.Print(err)
		return
	}
	log.Printf("validating code %v for %v\n", body.AuthCode, body.Email)

	if valid := ctx.codeMap.PopCode(body.Email, body.AuthCode); !valid {
		w.WriteHeader(404)
		log.Printf("code %v not found for %v\n", body.AuthCode, body.Email)
		return
	}

	token, err := ctx.getOAuthToken(body.Email)
	if err != nil {
		log.Print(err)
		w.WriteHeader(503)
		return
	}

	bytes, err := json.Marshal(tokenResponse{AccessToken: token})
	if err != nil {
		log.Panic(err)
	}
	w.Write(bytes)
}

// call this in a defer statement at the top of each handler. It allows the
// handlers to panic without crashing the process, and returns 500 where
// appropriate.
func recoverAndThrow500(w http.ResponseWriter) {
	if r := recover(); r != nil {
		log.Print(r)
		w.WriteHeader(500)
	}
}

func (ctx *context) getOAuthToken(email string) (string, error) {
	return ctx.tokenCreator.CreateToken(email)
}

func getTLSConfig(config map[string]string) *tls.Config {
	cert := config["email.sender.public_cert"]
	host := config["email.sender.public_host"]
	if cert == "" {
		return &tls.Config{
			ServerName: host,
		}
	}
	certPool := x509.NewCertPool()
	if !certPool.AppendCertsFromPEM([]byte(cert)) {
		log.Panic("unable to parse cert")
	}
	return &tls.Config{
		ServerName: host,
		RootCAs:    certPool,
	}

}

func getSMTPClient(config map[string]string) *smtp.Client {
	applianceHost := config["base.host.unified"]
	host := config["email.sender.public_host"]
	port := config["email.sender.public_port"]
	user := config["email.sender.public_username"]
	pass := config["email.sender.public_password"]
	enableTls := config["email.sender.public_enable_tls"]
	log.Print("smtp host: ", host)
	log.Print("smtp port: ", port)
	log.Print("smtp user: ", user)
	log.Print("smtp pass: ", pass)
	log.Print("smtp enable TLS: ", enableTls)

	addr := fmt.Sprint(host, ":", port)

	log.Print("smtp: connecting to ", addr)
	client, err := smtp.Dial(addr)
	if err != nil {
		log.Panic(err)
	}

	log.Println("smtp: saying HELO...")
	if err := client.Hello(applianceHost); err != nil {
		log.Panic(err)
	}

	if enableTls == "False" {
		log.Println("smtp: skipping tls upgrade...")
	} else {
		log.Println("smtp: upgrading to tls...")
		if err := client.StartTLS(getTLSConfig(config)); err != nil {
			log.Panic(err)
		}
	}

	if user != "" && pass != "" {
		log.Println("smtp: authenticating...")
		auth := smtp.PlainAuth("", user, pass, host)
		if err := client.Auth(auth); err != nil {
			log.Panic(err)
		}
	}

	return client
}

func sendEmailLoop(client *smtp.Client, ch <-chan EmailCodePair, config map[string]string) {
	defer client.Quit()
	sender := config["base.www.support_email_address"]
	for {
		p := <-ch
		log.Printf("sending code %v to %v\n", p.Code, p.Email)
		if err := client.Mail(sender); err != nil {
			log.Print("err in client.Mail: ", err)
			continue
		}
		if err := client.Rcpt(p.Email); err != nil {
			log.Print("err in client.Rcpt: ", err)
			continue
		}
		wc, err := client.Data()
		if err != nil {
			log.Print("err in client.Data: ", err)
			continue
		}
		body := fmt.Sprint(
			"From: AeroFS <", sender, ">\r\n",
			"To: ", p.Email, "\r\n",
			"Subject: Your Eyja Authorization Code: ", p.Code, "\r\n",
			"Enter this authorization code to access Eyja: ", p.Code, "\r\n",
		)
		if _, err := wc.Write([]byte(body)); err != nil {
			log.Print("err writing email body: ", err)
			continue
		}
		if err := wc.Close(); err != nil {
			log.Print("err closing email body: ", err)
			continue
		}
	}
}

func main() {
	service.ServiceBarrier()

	log.Println("getting config...")
	config, err := service.NewConfigClient("trifrost").Get()
	if err != nil {
		log.Panic(err)
	}

	mailChan := make(chan EmailCodePair, MAIL_QUEUE_SIZE)
	mailClient := getSMTPClient(config)

	go sendEmailLoop(mailClient, mailChan, config)

	deploymentSecret := service.ReadDeploymentSecret()
	clientSecret, err := getOAuthClientSecret(deploymentSecret, OAUTH_CLIENT_ID)
	if err != nil {
		log.Panic(err)
	}

	ctx := &context{
		codeMap: NewCodeMap(),
		mail:    mailChan,
		tokenCreator: &TokenCreator{
			clientId:         OAUTH_CLIENT_ID,
			clientSecret:     clientSecret,
			deploymentSecret: deploymentSecret,
		},
	}
	router := httprouter.New()
	router.POST("/auth_code", ctx.handleAuthCode)
	router.POST("/token", ctx.handleToken)
	log.Print("listening on ", PORT)
	log.Panic(http.ListenAndServe(fmt.Sprint(":", PORT), router))
}
