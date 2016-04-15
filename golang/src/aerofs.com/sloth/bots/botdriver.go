// This package contains each bot-type's associated handler
// The bot message blobs are forwarded in the message along with any post-processing
// necessary for the frontend. Each transform performs few checks
// to ensure the incoming webhook-http request body is reasonably formed.

package botdriver

import (
	"aerofs.com/sloth/bots/aerofs"
	"aerofs.com/sloth/bots/default"
	"aerofs.com/sloth/bots/github"
	"errors"
	"log"
	"net/http"
)

// Each bot is given the corresponding headers and request body
type handlePayload func(bytes []byte, store http.Header) ([]byte, error)

var botMap = map[int]handlePayload{
	// Regular
	1: aerofsbot.Transform,

	// Github
	2: githubbot.Transform,

	// Jira
	3: defaultbot.Transform,
}

// Perform basic validity checks, necessary transforms
func TransformBotMessage(botType int, body []byte, headers http.Header) ([]byte, error) {
	log.Printf("The bottype is %v", botType)
	if len(body) == 0 {
		return nil, errors.New("Webhook message has no body")
	}

	handler, ok := botMap[botType]
	if !ok {
		return nil, errors.New("Bot has no associated handler")
	}

	return handler(body, headers)
}
