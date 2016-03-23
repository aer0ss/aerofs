package util

import (
	"aerofs.com/sloth/errors"
	"crypto/md5"
	"crypto/rand"
	"encoding/hex"
	"sort"
	"strings"
)

// returns hex-encoded 128-bits
func GenerateRandomId() string {
	b := make([]byte, 16)
	_, err := rand.Read(b)
	errors.PanicOnErr(err)
	return hex.EncodeToString(b)
}

// Channel IDs are hex-encoded random 128-bit strings where the left-most
// nibble is 0x0.
func GenerateChannelId() string {
	return "0" + GenerateRandomId()[1:]
}

// Direct Convo IDs are generated by taking the MD5 of the space-separated,
// alphabetically-sorted list of convo-members, and setting the left-most
// nibble to 0x1.
func GenerateDirectConvoId(members []string) string {
	sort.Strings(members)
	hash := md5.Sum([]byte(strings.Join(members, " ")))
	return "1" + hex.EncodeToString(hash[:])[1:]
}

func GenerateFileConvoId(fileId string) string {
	hash := md5.Sum([]byte(fileId))
	return "2" + hex.EncodeToString(hash[:])[1:]
}
