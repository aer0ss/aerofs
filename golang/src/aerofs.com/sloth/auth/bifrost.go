// this file describes an implementation of TokenVerifier that uses Bifrost's
// GET /tokeninfo
package auth

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
)

// resource owner creds: base64(id:secret)
const BASIC_AUTH = "b2F1dGgtaGF2cmU6aS1hbS1ub3QtYS1yZXN0ZnVsLXNlY3JldA=="

// token verification route
const BIFROST_VERIFY_URL = "http://sparta.service:8700/tokeninfo"

//
// error type for unexpected errors communicating with Bifrost
//

type BifrostError struct {
	StatusCode int
	Body       string
}

func (e *BifrostError) Error() string {
	return fmt.Sprintf("%v -- %v", e.StatusCode, e.Body)
}

//
// Bifrost token verification response
// This is not the complete response, but probably all we might care about
//

type BifrostVerifyResponse struct {
	ExpiresIn int      `json:"expires_in"`
	Scopes    []string `json:"scopes"`
	Principal struct {
		Name string `json:"name"`
	}
}

//
// implementation of the TokenVerifier interface
//

type bifrostTokenVerifier struct {
	cache *TokenCache
}

// Verify if a token is authentic and has a corresponding UID
func (v *bifrostTokenVerifier) VerifyToken(token string) (string, error) {
	// check cache
	log.Printf("verify token against bifrost: %v\n", token)
	tokenChannel := v.cache.Get(token)
	future := <-tokenChannel

	return future.uid, future.err
}

func requestVerify(token string) (string, error) {
	// compose request
	url := BIFROST_VERIFY_URL + "?access_token=" + token
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return "", err
	}
	req.Header.Add("Authorization", "Basic "+BASIC_AUTH)
	// make request
	client := new(http.Client)
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	// check response status code
	if resp.StatusCode == 404 || resp.StatusCode == 410 {
		return "", TokenNotFoundError{Token: token}
	} else if resp.StatusCode != 200 {
		body, err := ioutil.ReadAll(resp.Body)
		if err != nil {
			log.Print("failed to read bifrost error response", err)
			return "", err
		}
		return "", &BifrostError{StatusCode: resp.StatusCode, Body: string(body)}
	}
	// assuming response is 200, read body and return the owner
	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		log.Print("failed to read bifrost 200 response: ", err)
		return "", err
	}
	var jsonResponse BifrostVerifyResponse
	if err := json.Unmarshal(body, &jsonResponse); err != nil {
		log.Print("failed to unmarshal json from bifrost response: ", err)
		return "", err
	}
	return jsonResponse.Principal.Name, nil
}

func NewBifrostTokenVerifier(cache *TokenCache) TokenVerifier {
	return &bifrostTokenVerifier{cache: cache}
}
