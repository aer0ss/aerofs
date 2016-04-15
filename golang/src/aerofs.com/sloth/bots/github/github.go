// This package will parse a github webhook message and convert to a useful
// form for Eyja.
//
// General Notes on GitHub Webhooks (https://developer.github.com/webhooks/)
//  - The event type is in the "X-GitHub-Event" header
//  - A webhook message is considered well-formed if the required Github
//    header_event is present

package githubbot

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
)

const (
	HEADER_EVENT             = "X-GitHub-Event"
	HEADER_HMAC              = "X-Hub-Signature"
	HEADER_UID               = "X-GitHub-Delivery"
	HEADER_USER_AGENT_PREFIX = "GitHub/Hookshot/"
)

// Tack on the corresponding event_type header to the given message
func Transform(bytes []byte, headers http.Header) ([]byte, error) {
	event := headers.Get(HEADER_EVENT)
	if event == "" {
		return nil, errors.New(fmt.Sprintf("Required %v header not present", HEADER_EVENT))
	}

	message, err := json.Marshal(struct {
		EventType string                `json:"event_type"`
		Body      serializedGithubEvent `json:"body"`
	}{
		EventType: event,
		Body:      serializedGithubEvent(bytes),
	})

	if err != nil {
		return nil, err
	}
	return message, nil
}

// Prevent reserialization of the already serialized github-event by implementing
// the marshaller interface
type serializedGithubEvent []byte

func (e serializedGithubEvent) MarshalJSON() ([]byte, error) {
	return []byte(e), nil
}
