// Package sparta provides a nice API for Sparta's RESTful API
package sparta

import (
	"aerofs.com/sloth/httpClientPool"
	"bytes"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
)

const (
	HTTP_CLIENT_POOL_SIZE = 10
	BASE_URL              = "http://sparta.service:8085/v1.4"
	DUMMY_DID             = "00000000000000000000000000000000"
)

// Client has helpful methods which wrap the Sparta API
type Client struct {
	deploymentSecret string
	pool             httpClientPool.Pool
}

// Unmarshal sparta /devices/{did} json response and discard everything except
// the owner UID
type spartaDIDResponse struct {
	Owner string
}

// GetDeviceOwner returns the UID of the device's owner
func (c *Client) GetDeviceOwner(did string) (string, error) {
	url := BASE_URL + "/devices/" + did
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return "", err
	}
	req.Header.Add("Authorization", "Aero-Service-Shared-Secret sloth "+c.deploymentSecret)
	resp := <-c.pool.Do(req)
	if resp.Err != nil {
		return "", resp.Err
	}
	if resp.R.StatusCode != 200 {
		return "", errors.New(fmt.Sprint(resp.R.StatusCode, " fetching owner for DID ", did))
	}
	var spartaResponse spartaDIDResponse
	if err := json.Unmarshal(resp.Body, &spartaResponse); err != nil {
		return "", err
	}
	return spartaResponse.Owner, nil
}

type spartaSharedFolderResponse struct {
	Id string
}

// CreateSharedFolder returns the SID of the newly-created shared folder
func (c *Client) CreateSharedFolder(uid, name string) (string, error) {
	url := BASE_URL + "/shares"
	body, err := json.Marshal(map[string]string{"name": name})
	if err != nil {
		return "", err
	}
	req, err := http.NewRequest("POST", url, bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Add("Authorization", getDelegatedAuthHeader(uid, c.deploymentSecret))
	req.Header.Add("Content-Type", "application/json")
	resp := <-c.pool.Do(req)
	if resp.Err != nil {
		return "", resp.Err
	}
	if resp.R.StatusCode != 201 {
		return "", errors.New(fmt.Sprint(resp.R.StatusCode, " creating shared folder"))
	}
	var response spartaSharedFolderResponse
	if err := json.Unmarshal(resp.Body, &response); err != nil {
		return "", err
	}
	return response.Id, nil
}

func (c *Client) AddSharedFolderMember(sid, uid string) error {
	url := fmt.Sprint(BASE_URL, "/shares/", sid, "/members")
	body, err := json.Marshal(map[string]interface{}{
		"email":       uid,
		"permissions": []string{"WRITE", "MANAGE"},
	})
	if err != nil {
		return err
	}
	req, err := http.NewRequest("POST", url, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Add("Authorization", getDelegatedAuthHeader(uid, c.deploymentSecret))
	req.Header.Add("Content-Type", "application/json")
	resp := <-c.pool.Do(req)
	if resp.Err != nil {
		return resp.Err
	}
	if resp.R.StatusCode != 201 && resp.R.StatusCode != 409 {
		return errors.New(fmt.Sprint(resp.R.StatusCode, " adding ", uid, " to ", sid))
	}
	return nil
}

func NewClient(deploymentSecret string) *Client {
	return &Client{
		deploymentSecret: deploymentSecret,
		pool:             httpClientPool.NewPool(HTTP_CLIENT_POOL_SIZE),
	}
}

func getDelegatedAuthHeader(uid, secret string) string {
	return strings.Join([]string{
		"Aero-Delegated-User-Device",
		"sloth",
		secret,
		base64.StdEncoding.EncodeToString([]byte(uid)),
		DUMMY_DID,
	}, " ")
}
