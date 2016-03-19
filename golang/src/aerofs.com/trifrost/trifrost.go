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
	"aerofs.com/service/config"
	"aerofs.com/trifrost/mailer"
	"encoding/json"
	"fmt"
	"github.com/aerofs/httprouter"
	"log"
	"net/http"
)

const (
	// default port on which to listen
	PORT = 8888

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

// context contains data required by the handlers
type context struct {

	// stores (email, auth code) pairs
	codeMap CodeMap

	// call mail.Mail(email, code) to send mail asynchronously
	mail mailer.Mailer

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

	ctx.mail.Mail(body.Email, authCode)
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

func main() {
	service.ServiceBarrier()

	log.Println("getting config...")
	cfg, err := config.NewClient("trifrost").Get()
	if err != nil {
		log.Panic(err)
	}

	mail := mailer.FromConfig(cfg)

	deploymentSecret := service.ReadDeploymentSecret()
	clientSecret, err := getOAuthClientSecret(deploymentSecret, OAUTH_CLIENT_ID)
	if err != nil {
		log.Panic(err)
	}

	ctx := &context{
		codeMap: NewCodeMap(),
		mail:    mail,
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
