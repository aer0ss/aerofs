package test

import (
	"aerofs.com/analytics/db"
	"github.com/aerofs/httprouter"
	"io/ioutil"
	"log"
	"net/http/httptest"
	"time"
)

// MockClockImpl - a mock implementation of util.Clock
type MockClockImpl struct {
	now time.Time
}

// NewMockClockImpl - constructor for MockClockImpl
func NewMockClockImpl(t time.Time) *MockClockImpl {
	return &MockClockImpl{t}
}

// Now - returns internal time.Time
func (c *MockClockImpl) Now() time.Time {
	return c.now
}

// PassTime - adds a duration to internal time.Time
func (c *MockClockImpl) PassTime(dur time.Duration) {
	c.now = c.now.Add(dur)
}

// SetupTestStructs - setup and return test structs required for analytics tests
func SetupTestStructs(setupDB func(*db.BoltKV) error,
	initRouter func(*httprouter.Router, *db.BoltKV)) (*db.BoltKV, *httptest.Server) {

	file, err := ioutil.TempFile("", "analyticstestdb")
	if err != nil {
		log.Fatal("Failed to create temp file:", err)
	}

	db, err := db.NewBoltKV(file.Name(), setupDB)
	if err != nil {
		log.Fatal("Failed to create db:", err)
	}

	r := httprouter.New()
	initRouter(r, db)
	testServer := httptest.NewServer(r)

	return db, testServer
}
