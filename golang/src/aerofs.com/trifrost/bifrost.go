package main

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"strings"
)

const (
	SPARTA_BASE_URL             = "http://sparta.service:8700"
	DUMMY_DID                   = "00000000000000000000000000000000"
	DEFAULT_CLIENT_NAME         = "Trifrost"
	DEFAULT_CLIENT_REDIRECT_URI = "aerofs://redirect"
	DEFAULT_RESOURCE_SERVER_KEY = "oauth-havre"
	DEFAULT_CLIENT_EXPIRES      = "0"
)

type oauthClientResponse struct {
	Secret string `json:"secret"`
}

type oauthTokenResponse struct {
	AccessToken string `json:"access_token"`
}

// Returns the OAuth client secret, creating the client if necessary
func getOAuthClientSecret(deploymentSecret, clientId string) (string, error) {
	log.Print("getting secret for OAuth client ", clientId)
	url := SPARTA_BASE_URL + "/clients/" + clientId
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return "", err
	}
	req.Header.Add("Authorization", "Aero-Service-Shared-Secret trifrost "+deploymentSecret)
	resp, err := new(http.Client).Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	switch resp.StatusCode {
	case 200:
		return getSecretFromBody(resp.Body)

	case 404:
		return createOAuthClient(deploymentSecret, clientId)

	default:
		text := fmt.Sprint(resp.StatusCode, " getting client secret for ", clientId)
		return "", errors.New(text)
	}
}

// Creates the OAuth client and returns the secret
func createOAuthClient(deploymentSecret, clientId string) (string, error) {
	log.Print("creating OAuth client ", clientId)
	data := url.Values{}
	data.Set("client_id", clientId)
	data.Set("client_name", DEFAULT_CLIENT_NAME)
	data.Set("redirect_uri", DEFAULT_CLIENT_REDIRECT_URI)
	data.Set("resource_server_key", DEFAULT_RESOURCE_SERVER_KEY)
	data.Set("expires", DEFAULT_CLIENT_EXPIRES)
	body := strings.NewReader(data.Encode())
	req, err := http.NewRequest("POST", SPARTA_BASE_URL+"/clients", body)
	if err != nil {
		return "", err
	}
	req.Header.Add("Authorization", "Aero-Service-Shared-Secret trifrost "+deploymentSecret)
	req.Header.Add("Content-Type", "application/x-www-form-urlencoded")
	resp, err := new(http.Client).Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		text := fmt.Sprint(resp.StatusCode, " creating client ", clientId)
		return "", errors.New(text)
	}

	return getSecretFromBody(resp.Body)
}

func getSecretFromBody(body io.ReadCloser) (string, error) {
	b, err := ioutil.ReadAll(body)
	if err != nil {
		return "", err
	}
	var r oauthClientResponse
	err = json.Unmarshal(b, &r)
	if err != nil {
		return "", err
	}
	return r.Secret, nil
}

// Contains the context for creating access tokens through Bifrost
type TokenCreator struct {
	clientId, clientSecret string
	deploymentSecret       string
}

// Creates an OAuth access token through sparta for the given user
func (tc *TokenCreator) CreateToken(uid string) (string, error) {
	data := url.Values{}
	data.Set("client_id", tc.clientId)
	data.Set("client_secret", tc.clientSecret)
	data.Set("grant_type", "delegated")
	data.Set("scope", "files.read,files.write,files.appdata,acl.write")
	body := strings.NewReader(data.Encode())

	req, err := http.NewRequest("POST", SPARTA_BASE_URL+"/delegate/token", body)
	if err != nil {
		return "", err
	}
	req.Header.Add("Authorization", getDelegatedAuthHeader(uid, tc.deploymentSecret))
	req.Header.Add("Content-Type", "application/x-www-form-urlencoded")
	resp, err := new(http.Client).Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		text := fmt.Sprint(resp.StatusCode, " creating token for ", uid)
		return "", errors.New(text)
	}

	b, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	var r oauthTokenResponse
	err = json.Unmarshal(b, &r)
	if err != nil {
		return "", err
	}
	return r.AccessToken, nil
}

func getDelegatedAuthHeader(uid, secret string) string {
	return strings.Join([]string{
		"Aero-Delegated-User",
		"aerofs-trifrost",
		secret,
		base64.StdEncoding.EncodeToString([]byte(uid)),
	}, " ")
}
