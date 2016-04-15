// This package provides the default transform for an incoming webhook
//
// For incoming webhooks that need no pre-processing and have no easy-checks
// to determine the payload is well-formed, just return the stringified payload

package defaultbot

import (
	"net/http"
)

func Transform(bytes []byte, headers http.Header) ([]byte, error) {
	return bytes, nil
}
