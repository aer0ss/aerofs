// Package sparta provides a nice API for Sparta's RESTful API
package sparta

import (
	"aerofs.com/sloth/httpClientPool"
	. "aerofs.com/sloth/structs"
	"bytes"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"regexp"
	"strconv"
	"strings"
)

const (
	HTTP_CLIENT_POOL_SIZE = 10
	BASE_URL              = "http://sparta.service:8085/v1.4"
	DUMMY_DID             = "00000000000000000000000000000000"
)

type spartaDevice struct {
	Owner string
}

type spartaUser struct {
	Email string
}

type SharedFolder struct {
	Id      string
	Name    string
	Members []spartaUser
}

type spartaMembersResponse []spartaUser

// Client has helpful methods which wrap the Sparta API
type Client struct {
	deploymentSecret string
	pool             httpClientPool.Pool
}

// GetDeviceOwner returns the UID of the device's owner
func (c *Client) GetDeviceOwner(did string) (string, error) {
	log.Print("fetch device owner ", did)
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
	var spartaResponse spartaDevice
	if err := json.Unmarshal(resp.Body, &spartaResponse); err != nil {
		return "", err
	}
	return spartaResponse.Owner, nil
}

func (c *Client) CreateSharedFolder(uids []string, owner, name string) (string, error) {
	return c.createSharedFolder(uids, owner, name, false)
}

func (c *Client) CreateLockedSharedFolder(uids []string, owner, name string) (string, error) {
	return c.createSharedFolder(uids, owner, name, true)
}

// createSharedFolder returns the SID of the newly-created shared folder
func (c *Client) createSharedFolder(uids []string, owner, name string, isLocked bool) (string, error) {
	url := BASE_URL + "/shares"
	members := make([]map[string]interface{}, 0, len(uids))
	for _, uid := range uids {
		members = append(members, map[string]interface{}{
			"email":       uid,
			"permissions": []string{"WRITE", "MANAGE"},
		})
	}
	body, err := json.Marshal(map[string]interface{}{
		"name":      name,
		"members":   members,
		"is_locked": isLocked,
	})
	if err != nil {
		return "", err
	}

	log.Printf("New shared folder body : %v", string(body))
	log.Printf("Owner is %v", owner)
	req, err := http.NewRequest("POST", url, bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Add("Authorization", getDelegatedAuthHeader(owner, c.deploymentSecret))
	req.Header.Add("Content-Type", "application/json")
	resp := <-c.pool.Do(req)
	if resp.Err != nil {
		return "", resp.Err
	}
	if resp.R.StatusCode != 201 {
		return "", errors.New(fmt.Sprint(resp.R.StatusCode, " creating shared folder"))
	}
	var response SharedFolder
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

func (c *Client) RemoveSharedFolderMember(sid, uid string) error {
	url := fmt.Sprint(BASE_URL, "/shares/", sid, "/members/", uid)
	req, err := http.NewRequest("DELETE", url, nil)
	if err != nil {
		return err
	}
	req.Header.Add("Authorization", getDelegatedAuthHeader(uid, c.deploymentSecret))
	resp := <-c.pool.Do(req)
	if resp.Err != nil {
		return resp.Err
	}
	if resp.R.StatusCode != 200 {
		return errors.New(fmt.Sprint(resp.R.StatusCode, " removing ", uid, " from ", sid))
	}
	return nil
}

func (c *Client) GetSharedFolderMembers(sid string) ([]string, error) {
	log.Print("fetch members for ", sid)
	url := fmt.Sprint(BASE_URL, "/shares/", sid, "/members")
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Add("Authorization", "Aero-Service-Shared-Secret sloth "+c.deploymentSecret)
	resp := <-c.pool.Do(req)
	if resp.Err != nil {
		return nil, resp.Err
	}
	if resp.R.StatusCode != 200 {
		return nil, errors.New(fmt.Sprint(resp.R.StatusCode, " getting members for ", sid))
	}
	var membersResponse spartaMembersResponse
	if err := json.Unmarshal(resp.Body, &membersResponse); err != nil {
		return nil, err
	}

	members := make([]string, 0, len(membersResponse))
	for _, u := range membersResponse {
		members = append(members, u.Email)
	}

	return members, nil
}

func (c *Client) GetAllSharedFolders() (shares []SharedFolder, epoch uint64, err error) {
	url := fmt.Sprint(BASE_URL, "/users/:2/shares")
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return
	}
	req.Header.Add("Authorization", "Aero-Service-Shared-Secret sloth "+c.deploymentSecret)
	resp := <-c.pool.Do(req)
	if resp.Err != nil {
		err = resp.Err
		return
	}
	if resp.R.StatusCode != 200 {
		err = errors.New(fmt.Sprint(resp.R.StatusCode, " getting shared folders"))
		return
	}

	err = json.Unmarshal(resp.Body, &shares)
	if err != nil {
		return
	}

	epoch, err = getEpochFromResponse(resp.R)
	return
}

func (c *Client) CreateUser(user *User) error {
	code, err := c.userRequest(user, "POST", "/users")
	if err != nil {
		return err
	}
	if code != 201 {
		return errors.New(fmt.Sprintf("%v creating user %v\n", code, user))
	}
	return nil
}

func (c *Client) UpdateUser(user *User) error {
	code, err := c.userRequest(user, "PUT", "/users/"+user.Id)
	if err != nil {
		return err
	}
	if code != 200 {
		return errors.New(fmt.Sprintf("%v updating user %v\n", code, user))
	}
	return nil
}

func (c *Client) CreateOrUpdateUser(user *User) error {
	code, err := c.userRequest(user, "POST", "/users")
	if err != nil {
		return err
	}
	switch code {
	case 201:
		return nil
	case 409:
		return c.UpdateUser(user)
	default:
		return errors.New(fmt.Sprintf("%v creating user %v\n", code, user))
	}
}

func (c *Client) userRequest(user *User, method, path string) (int, error) {
	url := fmt.Sprint(BASE_URL, path)
	body, err := json.Marshal(map[string]interface{}{
		"email":      user.Id,
		"first_name": user.FirstName,
		"last_name":  user.LastName,
	})
	if err != nil {
		return 0, err
	}
	req, err := http.NewRequest(method, url, bytes.NewReader(body))
	if err != nil {
		return 0, err
	}
	req.Header.Add("Authorization", getDelegatedAuthHeader(":2", c.deploymentSecret))
	req.Header.Add("Content-Type", "application/json")
	resp := <-c.pool.Do(req)
	if resp.Err != nil {
		return 0, resp.Err
	}
	return resp.R.StatusCode, nil
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

func getEpochFromResponse(r *http.Response) (uint64, error) {
	etag := r.Header.Get("ETag")
	match := regexp.MustCompile("^W/\"([0-9a-fA-F]+)\"$").FindStringSubmatch(etag)
	if match == nil || len(match) != 2 {
		return 0, errors.New("invalid ETag: " + etag)
	}
	return strconv.ParseUint(match[1], 16, 64)
}
