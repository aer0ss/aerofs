package main

import (
	"crypto/rand"
	"encoding/hex"
	"github.com/stretchr/testify/require"
	"testing"
)

func newIDBytes() []byte {
	u := make([]byte, 16)
	_, err := rand.Read(u)
	if err != nil {
		panic(err)
	}
	return u
}

func newID() string {
	return hex.EncodeToString(newIDBytes())
}

func newUID() UID {
	uid, err := NewUIDFromHex(newID())
	if err != nil {
		panic(err)
	}
	return uid
}

func TestUID_shouldConvertBackAndForth(t *testing.T) {
	for i := 0; i < 10000; i++ {
		b := newIDBytes()
		h := hex.EncodeToString(b)
		dh, err := NewUIDFromHex(h)
		require.Nil(t, err)
		db := NewUIDFromBytes(b)
		require.Equal(t, dh, db)
		require.Equal(t, h, dh.String())
		require.Equal(t, h, db.String())
		var buf [16]byte
		dh.Encode(buf[:])
		require.Equal(t, b, buf[:])
	}
}
