// A default bot is a bot that responds to basic incoming webhook messages
// containing a "body" attribute that contains the necessary text

package aerofsbot

import (
	"encoding/json"
	"errors"
	"net/http"
)

func Transform(bytes []byte, headers http.Header) ([]byte, error) {
	var event defaultEvent

	if err := json.Unmarshal(bytes, &event); err != nil {
		return nil, err
	}
	if event.Body == "" {
		return nil, errors.New("Body field is empty on a default bot message")
	}

	return bytes, nil
}

type defaultEvent struct {
	Body string `json:"body"`
}
