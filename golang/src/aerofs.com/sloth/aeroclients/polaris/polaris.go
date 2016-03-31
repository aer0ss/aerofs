// Package polaris provides a nice API for Polaris's RESTful API
package polaris

import (
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/httpClientPool"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
)

const (
	HTTP_CLIENT_POOL_SIZE = 10
	TRANSFORMS_BASE_URL   = "http://polaris.service:8086/transforms/"
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
	errors.PanicOnErr(err)
	req.Header.Add("Authorization", "Aero-Service-Shared-Secret sloth "+c.deploymentSecret)
	resp := <-c.pool.Do(req)
	errors.PanicOnErr(resp.Err)
	if resp.R.StatusCode != 200 {
		log.Panicf("%v %v\n", resp.R.StatusCode, string(resp.Body))
	}

	var responseData transformsResponse
	err = json.Unmarshal(resp.Body, &responseData)
	errors.PanicOnErr(err)

	// unmarshal only the fields in the transform struct, and attach the entire
	// json blob to the struct for storage in the db
	var transforms = make([]*Transform, 0, len(responseData.Transforms))
	for _, raw := range responseData.Transforms {
		var t = new(Transform)
		err := json.Unmarshal(raw, t)
		errors.PanicOnErr(err)
		t.Raw = string(raw)
		transforms = append(transforms, t)
	}
	return transforms
}

func NewClient(deploymentSecret string) *Client {
	return &Client{
		deploymentSecret: deploymentSecret,
		pool:             httpClientPool.NewPool(HTTP_CLIENT_POOL_SIZE),
	}
}
