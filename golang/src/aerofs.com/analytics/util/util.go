package util

import (
	"encoding/binary"
	"log"
	"time"
)

// Clock - interface to allow global time mocking for tests
type Clock interface {
	Now() time.Time
}

// DefaultClockImpl - an implementation of Clock which uses the system time
type DefaultClockImpl struct{}

// Now - returns current time in UTC timezone
func (c *DefaultClockImpl) Now() time.Time {
	return time.Now().UTC()
}

// encoding helper functions

// BytesToTime - convert byte slice interpreted as RFC3339 to time.Time
func BytesToTime(b []byte) time.Time {
	t, err := time.Parse(time.RFC3339, string(b))
	if err != nil {
		log.Panicln("bytesToTime:", err)
	}
	return t
}

// TimeToBytes - convert time.Time to byte slice interpreted as RFC3339
func TimeToBytes(t time.Time) []byte {
	return []byte(t.Format(time.RFC3339))
}

// EncodeUint64 - convert uint64 to byte slice
func EncodeUint64(n uint64) []byte {
	b := make([]byte, 8)
	binary.LittleEndian.PutUint64(b, n)
	return b
}

// DecodeUint64 - convert byte slice to uint64
func DecodeUint64(b []byte) uint64 {
	return binary.LittleEndian.Uint64(b)
}

// EncodeBool - convert bool to byte slice
func EncodeBool(b bool) []byte {
	if b {
		return EncodeUint64(1)
	}
	return EncodeUint64(0)
}

// DecodeBool - convert byte slice to bool
func DecodeBool(b []byte) bool {
	v := DecodeUint64(b)
	return v != 0
}
