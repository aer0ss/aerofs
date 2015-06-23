package auth

import (
	"encoding/hex"
	"github.com/stretchr/testify/assert"
	"testing"
)

func alphabetEncode(d []byte) string {
	var r []byte
	for i := 0; i < len(d); i++ {
		r = append(r, ((d[i]>>4)&0xf)+'a')
		r = append(r, (d[i]&0xf)+'a')
	}
	return string(r)
}

func cn(u, d string) string {
	return alphabetEncode(deviceCN([]byte(u), []byte(d)))
}

func TestMatchingDeviceCN_match_known(t *testing.T) {
	d, _ := hex.DecodeString("9cda745dd6cb4973afb60964bb22b294")
	assert.True(t, MatchingDeviceCN("emgnnbcmhhgiojbgfgnhaddndophfmemdjoeiadfbflgoafoimlpdhkncikgmnpd", []byte("hugues+lp@aerofs.com"), d))
}

func TestMatchingDeviceCN_match(t *testing.T) {
	assert.True(t, MatchingDeviceCN(cn("foo", "bar"), []byte("foo"), []byte("bar")))
}

func TestMatchingDeviceCN_mismatch_swap(t *testing.T) {
	assert.False(t, MatchingDeviceCN(cn("bar", "foo"), []byte("foo"), []byte("bar")))
}

func TestMatchingDeviceCN_mismatch_user(t *testing.T) {
	assert.False(t, MatchingDeviceCN(cn("qux", "bar"), []byte("foo"), []byte("bar")))
}

func TestMatchingDeviceCN_mismatch_device(t *testing.T) {
	assert.False(t, MatchingDeviceCN(cn("foo", "qux"), []byte("foo"), []byte("bar")))
}

func TestMatchingDeviceCN_mismatch_both(t *testing.T) {
	assert.False(t, MatchingDeviceCN(cn("baz", "qux"), []byte("foo"), []byte("bar")))
}
