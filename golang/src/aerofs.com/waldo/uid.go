package main

import (
	"encoding/binary"
	"fmt"
	"strconv"
)

// manipulating 64bit integers is more efficient than manipulating bytes
type UID [2]uint64

func (uid UID) Encode(buf []byte) {
	binary.BigEndian.PutUint64(buf[0:8], uid[0])
	binary.BigEndian.PutUint64(buf[8:16], uid[1])
}

var digits []byte = []byte{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'}

func (uid UID) String() string {
	var buf [32]byte
	for i := 0; i < 2; i++ {
		a := uid[i]
		for j := 0; j < 16; j++ {
			buf[16*i+15-j] = digits[a&0xf]
			a >>= 4
		}
	}
	return string(buf[:])
}

// Use big-endian so [2]uint64 sort like [16]byte
func NewUIDFromBytes(uid []byte) UID {
	if len(uid) != 16 {
		panic("invalid did")
	}
	var r UID
	r[0] = binary.BigEndian.Uint64(uid[0:8])
	r[1] = binary.BigEndian.Uint64(uid[8:16])
	return r
}

func NewUIDFromHex(hex string) (UID, error) {
	var uid UID
	var err error
	if len(hex) != 32 {
		return uid, fmt.Errorf("invalid did")
	}
	if uid[0], err = strconv.ParseUint(hex[0:16], 16, 64); err != nil {
		return uid, err
	}
	if uid[1], err = strconv.ParseUint(hex[16:32], 16, 64); err != nil {
		return uid, err
	}
	return uid, nil
}
