package segment

import (
	"bytes"
	"encoding/json"
	"errors"
	"io/ioutil"
	"log"
	"net/http"
	"strconv"
	"time"
)

// Client - client for sending messages to segment
type Client struct {
	Client   *http.Client
	Endpoint string
	Token    string
}

// Message fields common to all.
//TODO: licensing on this code? copied from segment
type Message struct {
	Context   map[string]interface{} `json:"context,omitempty"`
	Type      string                 `json:"type,omitempty"`
	MessageID string                 `json:"messageId,omitempty"`
	Timestamp *time.Time             `json:"timestamp,omitempty"`
	SentAt    string                 `json:"sentAt,omitempty"`
}

// Batch message.
type Batch struct {
	Messages []interface{} `json:"batch"`
	Message
}

// Identify message.
type Identify struct {
	Context      map[string]interface{} `json:"context,omitempty"`
	Integrations map[string]interface{} `json:"integrations,omitempty"`
	Traits       map[string]interface{} `json:"traits,omitempty"`
	AnonymousID  string                 `json:"anonymousId,omitempty"`
	UserID       string                 `json:"userId,omitempty"`
	Message
}

// Track message.
type Track struct {
	Context      map[string]interface{} `json:"context,omitempty"`
	Integrations map[string]interface{} `json:"integrations,omitempty"`
	Properties   map[string]interface{} `json:"properties,omitempty"`
	AnonymousID  string                 `json:"anonymousId,omitempty"`
	UserID       string                 `json:"userId,omitempty"`
	Event        string                 `json:"event"`
	Message
}

// Track - add a segment Track message to a batch
func (b *Batch) Track(t *Track) error {
	if t.UserID == "" && t.AnonymousID == "" {
		return errors.New("UserId or AnonymousId is required")
	}
	if t.Event == "" {
		return errors.New("Event is a required field")
	}
	t.Type = "track"
	b.Messages = append(b.Messages, t)
	return nil
}

// Identify - add a segment Identify message to a batch
func (b *Batch) Identify(i *Identify) error {
	if i.UserID == "" && i.AnonymousID == "" {
		return errors.New("UserId or AnonymousId is required")
	}
	i.Type = "identify"
	b.Messages = append(b.Messages, i)
	return nil
}

// Track - send a track message to segment client endpoint
func (c *Client) Track(t *Track) error {
	return c.send(t, "/track")
}

// Identify - send an identify message to segment client endpoint
func (c *Client) Identify(i *Identify) error {
	return c.send(i, "/identify")
}

// Batch - send a batch message to segment client endpoint
func (c *Client) Batch(b *Batch) error {
	return c.send(b, "/batch")
}

// Send - send a segmentio message to client's endpoint
func (c *Client) send(message interface{}, route string) error {
	content, err := json.Marshal(message)
	if err != nil {
		return errors.New("Failed to marshal segment message: " + err.Error())
	}
	log.Println("Send: ", string(content))
	url := c.Endpoint + "/v1" + route
	req, err := http.NewRequest("POST", url, bytes.NewBuffer(content))
	req.Header.Add("Content-Type", "application/json")
	req.Header.Add("Content-Length", string(len(content)))
	req.SetBasicAuth(c.Token, "")

	// return error if there is a problem sending, so we can retry later
	resp, err := c.Client.Do(req)
	if err != nil {
		return errors.New("Failed to send message to segment: " + err.Error())
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		body, err := ioutil.ReadAll(resp.Body)
		msg := string(body)
		if err != nil {
			msg = "Failed to read segment error: " + err.Error()
		}
		if resp.StatusCode >= 400 {
			return errors.New("Error received from segment: " + msg)
		}
		return errors.New("Received unexpected status code from segment: " +
			strconv.Itoa(resp.StatusCode))
	}
	return nil
}
