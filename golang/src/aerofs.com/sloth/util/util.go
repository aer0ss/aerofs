package util

import (
	"aerofs.com/sloth/errors"
	"crypto/rand"
	"encoding/hex"
)

// returns hex-encoded 128-bits
func GenerateRandomId() string {
	b := make([]byte, 16)
	_, err := rand.Read(b)
	errors.PanicOnErr(err)
	return hex.EncodeToString(b)
}
