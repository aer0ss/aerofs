// Package polaris provides a nice API for Polaris's RESTful API
package polaris

import (
	myErrors "aerofs.com/sloth/errors"
	"aerofs.com/sloth/httpClientPool"
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"strings"
	"encoding/base64"
)

const (
	HTTP_CLIENT_POOL_SIZE = 10
	OBJECTS_BASE_URL      = "http://polaris.service:8086/objects/"
	TRANSFORMS_BASE_URL   = "http://polaris.service:8086/transforms/"
	DUMMY_DID             = "00000000000000000000000000000000"
)

// Client has helpful methods which wrap the Sparta API
type Client struct {
	deploymentSecret string
	pool             httpClientPool.Pool
}

// Transform represents a single Polaris transform object as returned by the
// Polaris API. The original json blob returned by Polaris is stored in the Raw
// field. Only some fields are decoded prior to storage in the db. The Uid
// field is included as a convenience and must be populated prior to insertion
// into the db.
type Transform struct {
	LogicalTimestamp int64  `json:"logical_timestamp"` // unmarshal this to check for duplicates
	Originator       string `json:"originator"`        // unmarshal this to translate to a UID
	Store            string `json:"store"`
	Oid              string `json:"oid"`
	NewVersion       int    `json:"new_version"`
	Uid              string
	Raw              string
}

type transformsResponse struct {
	MaxTransformCount int64             `json:"max_transform_count"`
	Transforms        []json.RawMessage `json:"transforms"`
}

func (c *Client) GetTransforms(sid string, since int64) []*Transform {
	url := fmt.Sprint(TRANSFORMS_BASE_URL, sid, "?count=50&since=", since)
	req, err := http.NewRequest("GET", url, nil)
	myErrors.PanicOnErr(err)
	req.Header.Add("Authorization", "Aero-Service-Shared-Secret sloth "+c.deploymentSecret)
	resp := <-c.pool.Do(req)
	myErrors.PanicOnErr(resp.Err)
	if resp.R.StatusCode != 200 {
		log.Panicf("%v %v\n", resp.R.StatusCode, string(resp.Body))
	}

	var responseData transformsResponse
	err = json.Unmarshal(resp.Body, &responseData)
	myErrors.PanicOnErr(err)

	// unmarshal only the fields in the transform struct, and attach the entire
	// json blob to the struct for storage in the db
	var transforms = make([]*Transform, 0, len(responseData.Transforms))
	for _, raw := range responseData.Transforms {
		var t = new(Transform)
		err := json.Unmarshal(raw, t)
		myErrors.PanicOnErr(err)
		t.Raw = string(raw)
		transforms = append(transforms, t)
	}
	return transforms
}

// calls polaris to update the convo members' shared folder name
func (c *Client) UpdateSharedFolderName(sid, oldName string, newName string, uid string) (error) {
	url := OBJECTS_BASE_URL + sid
	body, err := json.Marshal(map[string]interface{}{
		"type"    : "RENAME_STORE",
		"old_name" : oldName,
		"new_name" : newName,
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
	if resp.R.StatusCode != 200 {
		return errors.New(fmt.Sprint(resp.R.StatusCode, " updating shared folder name"))
	}
	return nil
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

func NewClient(deploymentSecret string) *Client {
	return &Client{
		deploymentSecret: deploymentSecret,
		pool:             httpClientPool.NewPool(HTTP_CLIENT_POOL_SIZE),
	}
}
