package main

import (
	"aerofs.com/analytics/db"
	"aerofs.com/analytics/test"
	"aerofs.com/analytics/util"
	"aerofs.com/service/auth"
	"bytes"
	"errors"
	"github.com/aerofs/httprouter"
	"github.com/boltdb/bolt"
	"log"
	"net/http"
	"os"
	"testing"
	"time"
)

var mockClock = test.NewMockClockImpl(time.Now().UTC())

// db test setup
var dbFile string

func initializeRouter(r *httprouter.Router, db *db.BoltKV) {
	r.Handle("POST", "/events", auth.OptionalAuth(eventHandler(db)))
}

var badReqTests = []string{
	``,
	`{"event":"test","iaminvalidjson}`,
	`{"event":"test"}`,
	`{"event":"USER_SIGNUP"}`,
	`{"event":"USER_SIGNUP","value":2}`,
	`{"event":"USER_SIGNUP","value":"2"}`,
	`{"event":"USER_SIGNUP","value":}`,
	`{"event":"USER_SIGNUP","value":0}`,
	`{"event":"USER_SIGNUP","user_id":"1234","value":1}`,
	`{"event":"LINK_CREATED","value":1}`,
	`{"event":""}`,
}

func TestAnalytics_Send_invalid_event_should_return_400(t *testing.T) {
	db, testServer := test.SetupTestStructs(setupDB, initializeRouter)
	defer testServer.Close()
	defer db.Close()

	for i, testCase := range badReqTests {
		reqBody := []byte(testCase)

		if testing.Verbose() {
			log.Println("Test case", i)
			log.Println("Test input: `" + testCase + "`")
		}

		res, _ := http.Post(testServer.URL+"/events", "application/json", bytes.NewBuffer(reqBody))
		if res.StatusCode != http.StatusBadRequest {
			t.Errorf("Expected response status code to be %v. Got: %v", http.StatusBadRequest, res.StatusCode)
		}

		if testing.Verbose() {
			log.Println()
		}
	}
}

var testSuccess1_1 = `{"event":"USER_SIGNUP","value":1}`
var testSuccess1_2 = `{"event":"LINK_CREATED","user_id":"123abc","value":1}`
var testKey1_1 = []byte("USER_SIGNUP")
var testKey1_2 = []byte("LINK_CREATED")

func TestAnalytics_Send_valid_event_should_return_200_and_persist(t *testing.T) {
	db, testServer := test.SetupTestStructs(setupDB, initializeRouter)
	defer db.Close()
	defer testServer.Close()

	reqs := [][]byte{
		[]byte(testSuccess1_1),
		[]byte(testSuccess1_1),
		[]byte(testSuccess1_2),
	}

	// try to ensure events get into the same bucket
	for i, req := range reqs {
		res, _ := http.Post(testServer.URL+"/events", "application/json", bytes.NewBuffer(req))
		if res.StatusCode != http.StatusOK {
			t.Errorf("Expected response status code to be %v. Got: %v", http.StatusOK, res.StatusCode)
		}

		// advance time before sending the final request
		if i == 1 {
			mockClock.PassTime(EventsInterval)
		}
	}

	var count int
	db.View(func(tx *bolt.Tx) error {
		count = tx.Bucket(EventsKey).Stats().BucketN
		return nil
	})
	// top level bucket + 2 sub-buckets = 3
	if count != 3 {
		t.Errorf("Expected exactly 1 event sub-bucket. Got: %v", count-1)
	}

	var testCount1, testCount2 uint64
	var userBucketSize int
	err := db.View(func(tx *bolt.Tx) error {
		c := tx.Bucket(EventsKey).Cursor()
		k1, _ := c.Last()
		if k1 == nil {
			return errors.New("Nil key k1")
		}
		k2, _ := c.Prev()
		if k2 == nil {
			return errors.New("Nil key k2")
		}
		b1 := tx.Bucket(EventsKey).Bucket(k1)
		b2 := tx.Bucket(EventsKey).Bucket(k2)
		if v := b2.Get(testKey1_1); v != nil {
			testCount1 = util.DecodeUint64(v)
		}
		if v := b1.Get(testKey1_2); v != nil {
			testCount2 = util.DecodeUint64(v)
		}

		k1, _ = tx.Bucket(UsersKey).Cursor().Last()
		b1 = tx.Bucket(UsersKey).Bucket(k1)
		userBucketSize = b1.Stats().KeyN
		return nil
	})
	if err != nil {
		t.Errorf("Database error: %v", err.Error())
		return
	}

	if testCount1 != 2 {
		t.Errorf("Expected value of %v to be 2. Got: %v", testKey1_1, testCount1)
	}
	if testCount2 != 1 {
		t.Errorf("Expected value of %v to be 1. Got: %v", testKey1_2, testCount2)
	}
	if userBucketSize != 1 {
		t.Errorf("Expected # of active users in bucket to be 1. Got: %v", userBucketSize)
	}
}

var testKey2_1 = "LINK_CREATED"
var testVal2_1 = uint64(200)
var testHash2_1 = "1a2b3c4d"

func TestAnalytics_Current_bucket_events_should_not_be_sent(t *testing.T) {
	db, testServer := test.SetupTestStructs(setupDB, initializeRouter)
	defer db.Close()
	defer testServer.Close()

	tse := util.TimeToBytes(clock.Now().Truncate(EventsInterval))
	tsu := util.TimeToBytes(clock.Now().Truncate(UsersInterval))
	err := db.Update(func(tx *bolt.Tx) error {
		tx.Bucket(EventsKey).CreateBucket(tse)
		tx.Bucket(EventsKey).Bucket(tse).Put([]byte(testKey2_1), util.EncodeUint64(testVal2_1))
		tx.Bucket(UsersKey).CreateBucket(tsu)
		tx.Bucket(UsersKey).Bucket(tsu).Put([]byte(testHash2_1), PresentFlag)
		return nil
	})
	if err != nil {
		t.Errorf("DB error: %v", err.Error())
	}

	var tm map[string][]byte
	var mockSend = func(testMap map[string][]byte, t time.Time) error {
		tm = testMap
		return nil
	}

	util.SendBucket(db, EventsKey, EventsInterval, clock.Now(), mockSend)

	if len(tm) != 0 {
		t.Errorf("Expected # of events sent to be 0. Got: %v", len(tm))
	}

	tm = make(map[string][]byte)
	util.SendBucket(db, UsersKey, UsersInterval, clock.Now(), mockSend)

	if len(tm) != 0 {
		t.Errorf("Expected # of users sent to be 0. Got: %v", len(tm))
	}

	// ensure buckets were not deleted
	var eventSubbucketCount, userSubbucketCount int
	err = db.View(func(tx *bolt.Tx) error {
		eventSubbucketCount = tx.Bucket(EventsKey).Stats().BucketN - 1
		userSubbucketCount = tx.Bucket(UsersKey).Stats().BucketN - 1
		return nil
	})

	if eventSubbucketCount != 1 {
		t.Errorf("Expected # of event subbuckets to be 1. Got: %v", eventSubbucketCount)
	}
	if eventSubbucketCount != 1 {
		t.Errorf("Expected # of user subbuckets to be 1. Got: %v", userSubbucketCount)
	}
}

var testKey2_2 = "USER_SIGNUP"
var testVal2_2 = uint64(123)

func TestAnalytics_Old_bucket_events_should_be_sent(t *testing.T) {
	db, testServer := test.SetupTestStructs(setupDB, initializeRouter)
	defer db.Close()
	defer testServer.Close()

	tse := util.TimeToBytes(clock.Now().Truncate(EventsInterval))
	tsu := util.TimeToBytes(clock.Now().Truncate(UsersInterval))
	err := db.Update(func(tx *bolt.Tx) error {
		tx.Bucket(EventsKey).CreateBucket(tse)
		tx.Bucket(EventsKey).Bucket(tse).Put([]byte(testKey2_1), util.EncodeUint64(testVal2_1))
		tx.Bucket(UsersKey).CreateBucket(tsu)
		tx.Bucket(UsersKey).Bucket(tsu).Put([]byte(testHash2_1), PresentFlag)

		// create a second bucket
		mockClock.PassTime(EventsInterval)
		tse = util.TimeToBytes(clock.Now().Truncate(EventsInterval))
		tx.Bucket(EventsKey).CreateBucket(tse)
		tx.Bucket(EventsKey).Bucket(tse).Put([]byte(testKey2_2), util.EncodeUint64(testVal2_2))
		return nil
	})
	if err != nil {
		t.Errorf("DB error: %v", err.Error())
	}

	// ensure that enough time passes to trigger send for both cases
	mockClock.PassTime(EventsInterval + UsersInterval)

	var tm = make(map[string][]byte)
	var mockSend = func(testMap map[string][]byte, t time.Time) error {
		for k, v := range testMap {
			if val, ok := tm[k]; ok && len(val) != 0 {
				tm[k] = util.EncodeUint64(util.DecodeUint64(val) + util.DecodeUint64(v))
			} else {
				tm[k] = v
			}
		}
		return nil
	}

	util.SendBucket(db, EventsKey, EventsInterval, clock.Now(), mockSend)
	if v, ok := tm[testKey2_1]; !ok || testVal2_1 != util.DecodeUint64(v) {
		t.Errorf("Expected value not found for key: %v", testKey2_1)
	}
	if v, ok := tm[testKey2_2]; !ok || testVal2_2 != util.DecodeUint64(v) {
		t.Errorf("Expected value not found for key: %v", testKey2_2)
	}

	tm = make(map[string][]byte)
	util.SendBucket(db, UsersKey, UsersInterval, clock.Now(), mockSend)
	if _, ok := tm[testHash2_1]; !ok {
		t.Errorf("Expected user key not found: %v", testHash2_1)
	}

	// ensure buckets were not deleted
	var eventSubbucketCount, userSubbucketCount int
	err = db.View(func(tx *bolt.Tx) error {
		eventSubbucketCount = tx.Bucket(EventsKey).Stats().BucketN - 1
		userSubbucketCount = tx.Bucket(UsersKey).Stats().BucketN - 1
		return nil
	})

	if eventSubbucketCount != 0 {
		t.Errorf("Expected # of event subbuckets to be 0. Got: %v", eventSubbucketCount)
	}
	if eventSubbucketCount != 0 {
		t.Errorf("Expected # of user subbuckets to be 0. Got: %v", userSubbucketCount)
	}
}

func TestMain(m *testing.M) {
	// start setup
	clock = mockClock

	// remove timestamp and redirect to stdout for build script compat
	log.SetFlags(0)
	log.SetOutput(os.Stdout)
	log.Println("Test start time:", mockClock.Now())

	//end setup
	//run tests

	os.Exit(m.Run())
}
