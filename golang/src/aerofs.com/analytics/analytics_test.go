package main

import (
	"aerofs.com/service/auth"
	"bytes"
	"errors"
	"github.com/aerofs/httprouter"
	"github.com/boltdb/bolt"
	"io/ioutil"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"testing"
	"time"
)

// mock implementation of time
type mockClockImpl struct {
	now time.Time
}

func (c *mockClockImpl) Now() time.Time {
	return c.now
}
func (c *mockClockImpl) PassTime(dur time.Duration) {
	c.now = c.now.Add(dur)
}

var mockClock = mockClockImpl{time.Now()}

// db test setup
var dbFile string

func setupTestStructs() (*BoltKV, *httptest.Server) {
	file, err := ioutil.TempFile("", "analyticstestdb")
	if err != nil {
		log.Fatal("Failed to create temp file:", err)
	}

	db, err := NewBoltKV(file.Name(), setupDB)
	if err != nil {
		log.Fatal("Failed to create db:", err)
	}
	router := httprouter.New()
	router.Handle("POST", "/", auth.OptionalAuth(eventHandler(db)))
	testServer := httptest.NewServer(router)

	return db, testServer
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
	`{"event":"LINK_CREATED_DESKTOP","value":1}`,
	`{"event":""}`,
}

func TestAnalytics_Send_invalid_event_should_return_400(t *testing.T) {
	db, testServer := setupTestStructs()
	defer testServer.Close()
	defer db.Close()

	for i, testCase := range badReqTests {
		reqBody := []byte(testCase)

		if testing.Verbose() {
			log.Println("Test case", i)
			log.Println("Test input: `" + testCase + "`")
		}

		res, _ := http.Post(testServer.URL, "application/json", bytes.NewBuffer(reqBody))
		if res.StatusCode != http.StatusBadRequest {
			t.Errorf("Expected response status code to be " +
				strconv.Itoa(http.StatusBadRequest) + ". Got: " + strconv.Itoa(res.StatusCode))
		}

		if testing.Verbose() {
			log.Println()
		}
	}
}

var testSuccess1_1 = `{"event":"USER_SIGNUP","value":1}`
var testSuccess1_2 = `{"event":"BYTES_SYNCED","value":1234}`
var testSuccess1_3 = `{"event":"LINK_CREATED_DESKTOP","user_id":"123abc","value":1}`
var testKey1_1 = []byte("USER_SIGNUP")
var testKey1_2 = []byte("BYTES_SYNCED")
var testKey1_3 = []byte("LINK_CREATED_DESKTOP")

func TestAnalytics_Send_valid_event_should_return_200_and_persist(t *testing.T) {
	db, testServer := setupTestStructs()
	defer db.Close()
	defer testServer.Close()

	reqs := [][]byte{
		[]byte(testSuccess1_1),
		[]byte(testSuccess1_1),
		[]byte(testSuccess1_2),
		[]byte(testSuccess1_3),
	}

	// try to ensure events get into the same bucket
	for i, req := range reqs {
		res, _ := http.Post(testServer.URL, "application/json", bytes.NewBuffer(req))
		if res.StatusCode != http.StatusOK {
			t.Errorf("Expected response status code to be " +
				strconv.Itoa(http.StatusOK) + ". Got: " + strconv.Itoa(res.StatusCode))
		}

		// advance time before sending the final request
		if i == 2 {
			mockClock.PassTime(EventBucketInterval)
		}
	}

	var count int
	db.View(func(tx *bolt.Tx) error {
		count = tx.Bucket(EventBucketKey).Stats().BucketN
		return nil
	})
	// top level bucket + 2 sub-buckets = 3
	if count != 3 {
		t.Errorf("Expected exactly 1 event sub-bucket. Got: " + strconv.Itoa(count-1))
	}

	var testCount1, testCount2, testCount3 uint64
	var userBucketSize int
	err := db.View(func(tx *bolt.Tx) error {
		c := tx.Bucket(EventBucketKey).Cursor()
		k1, _ := c.Last()
		if k1 == nil {
			return errors.New("Nil key k1")
		}
		k2, _ := c.Prev()
		if testing.Verbose() {
			log.Println("k1:", bytesToTime(k1))
			log.Println("k2:", bytesToTime(k2))
		}
		if k2 == nil {
			return errors.New("Nil key k2")
		}
		b1 := tx.Bucket(EventBucketKey).Bucket(k1)
		b2 := tx.Bucket(EventBucketKey).Bucket(k2)
		if testing.Verbose() {
			log.Println("bucket1 count:", b1.Stats().KeyN)
			log.Println("bucket2 count:", b2.Stats().KeyN)
		}
		if v := b2.Get(testKey1_1); v != nil {
			testCount1 = decodeUint64(v)
		}
		if v := b2.Get(testKey1_2); v != nil {
			testCount2 = decodeUint64(v)
		}
		if v := b1.Get(testKey1_3); v != nil {
			testCount3 = decodeUint64(v)
		}

		k1, _ = tx.Bucket(UserBucketKey).Cursor().Last()
		b1 = tx.Bucket(UserBucketKey).Bucket(k1)
		userBucketSize = b1.Stats().KeyN
		return nil
	})
	if err != nil {
		t.Errorf("Database error: " + err.Error())
		return
	}

	if testCount1 != 2 {
		t.Errorf("Expected value of " + string(testKey1_1) + " to be 2. Got: " +
			strconv.FormatUint(testCount1, 10))
	}
	if testCount2 != 1234 {
		t.Errorf("Expected value of " + string(testKey1_2) + " to be 1234. Got: " +
			strconv.FormatUint(testCount2, 10))
	}
	if testCount3 != 1 {
		t.Errorf("Expected value of " + string(testKey1_3) + " to be 1. Got: " +
			strconv.FormatUint(testCount3, 10))
	}
	if userBucketSize != 1 {
		t.Errorf("Expected # of active users in bucket to be 1. Got: " +
			strconv.Itoa(userBucketSize))
	}
}

var testKey2_1 = "LINK_CREATED_DESKTOP"
var testVal2_1 = uint64(200)
var testHash2_1 = "1a2b3c4d"

func TestAnalytics_Current_bucket_events_should_not_be_sent(t *testing.T) {
	db, testServer := setupTestStructs()
	defer db.Close()
	defer testServer.Close()

	tse := timeToBytes(clock.Now().Truncate(EventBucketInterval))
	tsu := timeToBytes(clock.Now().Truncate(UserBucketInterval))
	err := db.Update(func(tx *bolt.Tx) error {
		tx.Bucket(EventBucketKey).CreateBucket(tse)
		tx.Bucket(EventBucketKey).Bucket(tse).Put([]byte(testKey2_1), encodeUint64(testVal2_1))
		tx.Bucket(UserBucketKey).CreateBucket(tsu)
		tx.Bucket(UserBucketKey).Bucket(tsu).Put([]byte(testHash2_1), PresentFlag)
		return nil
	})
	if err != nil {
		t.Errorf("DB error: " + err.Error())
	}

	var tm map[string][]byte
	var mockSend = func(testMap map[string][]byte, t time.Time) error {
		tm = testMap
		return nil
	}

	sendBucket(db, EventBucketKey, EventBucketInterval, mockSend)

	if len(tm) != 0 {
		t.Errorf("Expected # of events sent to be 0. Got: " + strconv.Itoa(len(tm)))
	}

	tm = make(map[string][]byte)
	sendBucket(db, UserBucketKey, UserBucketInterval, mockSend)

	if len(tm) != 0 {
		t.Errorf("Expected # of users sent to be 0. Got: " + strconv.Itoa(len(tm)))
	}

	// ensure buckets were not deleted
	var eventSubbucketCount, userSubbucketCount int
	err = db.View(func(tx *bolt.Tx) error {
		eventSubbucketCount = tx.Bucket(EventBucketKey).Stats().BucketN - 1
		userSubbucketCount = tx.Bucket(UserBucketKey).Stats().BucketN - 1
		return nil
	})

	if eventSubbucketCount != 1 {
		t.Errorf("Expected # of event subbuckets to be 1. Got: " + strconv.Itoa(eventSubbucketCount))
	}
	if eventSubbucketCount != 1 {
		t.Errorf("Expected # of user subbuckets to be 1. Got: " + strconv.Itoa(userSubbucketCount))
	}
}

var testKey2_2 = "USER_SIGNUP"
var testVal2_2 = uint64(123)

func TestAnalytics_Old_bucket_events_should_be_sent(t *testing.T) {
	db, testServer := setupTestStructs()
	defer db.Close()
	defer testServer.Close()

	tse := timeToBytes(clock.Now().Truncate(EventBucketInterval))
	tsu := timeToBytes(clock.Now().Truncate(UserBucketInterval))
	err := db.Update(func(tx *bolt.Tx) error {
		tx.Bucket(EventBucketKey).CreateBucket(tse)
		tx.Bucket(EventBucketKey).Bucket(tse).Put([]byte(testKey2_1), encodeUint64(testVal2_1))
		tx.Bucket(UserBucketKey).CreateBucket(tsu)
		tx.Bucket(UserBucketKey).Bucket(tsu).Put([]byte(testHash2_1), PresentFlag)

		// create a second bucket
		mockClock.PassTime(EventBucketInterval)
		tse = timeToBytes(clock.Now().Truncate(EventBucketInterval))
		tx.Bucket(EventBucketKey).CreateBucket(tse)
		tx.Bucket(EventBucketKey).Bucket(tse).Put([]byte(testKey2_2), encodeUint64(testVal2_2))
		return nil
	})
	if err != nil {
		t.Errorf("DB error: " + err.Error())
	}

	// ensure that enough time passes to trigger send for both cases
	mockClock.PassTime(EventBucketInterval + UserBucketInterval)

	var tm = make(map[string][]byte)
	var mockSend = func(testMap map[string][]byte, t time.Time) error {
		for k, v := range testMap {
			if val, ok := tm[k]; ok && len(val) != 0 {
				tm[k] = encodeUint64(decodeUint64(val) + decodeUint64(v))
			} else {
				tm[k] = v
			}
		}
		return nil
	}

	sendBucket(db, EventBucketKey, EventBucketInterval, mockSend)
	if v, ok := tm[testKey2_1]; !ok || testVal2_1 != decodeUint64(v) {
		log.Println(decodeUint64(v))
		t.Errorf("Expected value not found for key: " + testKey2_1)
	}
	if v, ok := tm[testKey2_2]; !ok || testVal2_2 != decodeUint64(v) {
		log.Println(decodeUint64(v))
		t.Errorf("Expected value not found for key: " + testKey2_2)
	}

	tm = make(map[string][]byte)
	sendBucket(db, UserBucketKey, UserBucketInterval, mockSend)
	if _, ok := tm[testHash2_1]; !ok {
		t.Errorf("Expected user key not found: " + testHash2_1)
	}

	// ensure buckets were not deleted
	var eventSubbucketCount, userSubbucketCount int
	err = db.View(func(tx *bolt.Tx) error {
		eventSubbucketCount = tx.Bucket(EventBucketKey).Stats().BucketN - 1
		userSubbucketCount = tx.Bucket(UserBucketKey).Stats().BucketN - 1
		return nil
	})

	if eventSubbucketCount != 0 {
		t.Errorf("Expected # of event subbuckets to be 0. Got: " + strconv.Itoa(eventSubbucketCount))
	}
	if eventSubbucketCount != 0 {
		t.Errorf("Expected # of user subbuckets to be 0. Got: " + strconv.Itoa(userSubbucketCount))
	}
}

func TestMain(m *testing.M) {
	// start setup
	clock = &mockClock

	// remove timestamp
	log.SetFlags(0)

	//end setup
	//run tests

	os.Exit(m.Run())
}
