package main

/*
   Analytics container
   Listens for events sent from other services, and aggregates them in boltDB
   Usage events are aggregated across 2-hour intervals, and sent periodically
   Daily active users are also stored in boltDB, similarly to usage events
   Other metrics are queried from other containers on a regular (daily) basis
   These events/metrics are sent to Segment, which forwards to Mixpanel
*/

import (
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"path"
	"strings"
	"time"

	"aerofs.com/analytics/segment"
	"aerofs.com/service"
	"aerofs.com/service/auth"
	"aerofs.com/service/config"
	"github.com/aerofs/httprouter"
	"github.com/boltdb/bolt"
)

//TODO change from testing values
//Frequency values here are more frequent than the eventual permanent values
//Values governing the frequency at which events are sent/queried
const (
	DailyMetricsInterval = time.Second * 10
	EventsInterval       = time.Second * 8
	UsersInterval        = time.Second * 10
	TickerInterval       = time.Second * 5

	SegmentWriteKey = ""
)

// const arrays are not possible in Go
var (
	companyID string

	DailyMetricsKey = []byte("dailymetrics")
	EventsKey       = []byte("events")
	UsersKey        = []byte("users")

	PresentFlag = []byte{}
)

var segmentClient = segment.Client{
	Client: http.DefaultClient,
	//Endpoint: "https://api.segment.io",
	Token: SegmentWriteKey,
}

// global time indirection to allow testing
type clockImpl struct{}

func (c *clockImpl) Now() time.Time {
	return time.Now().UTC()
}

var clock interface {
	Now() time.Time
} = &clockImpl{}

// BoltKV - wrapper around bolt.DB to allow future extensibility
type BoltKV struct {
	*bolt.DB
}

// NewBoltKV create or open a new Bolt database & call setup function
func NewBoltKV(filename string, setup func(*BoltKV) error) (*BoltKV, error) {
	db, err := bolt.Open(filename, 0600, nil)
	if err != nil {
		return nil, err
	}

	bkv := &BoltKV{db}

	err = setup(bkv)
	if err != nil {
		return nil, err
	}

	return bkv, nil
}

// initialize Bolt database
func setupDB(db *BoltKV) error {
	err := db.Update(func(tx *bolt.Tx) error {
		_, err := tx.CreateBucketIfNotExists(EventsKey)
		if err != nil {
			return err
		}
		_, err = tx.CreateBucketIfNotExists(UsersKey)
		if err != nil {
			return err
		}
		_, err = tx.CreateBucketIfNotExists(DailyMetricsKey)
		if err != nil {
			return err
		}

		return nil
	})
	if err != nil {
		return errors.New("Failed to setup DB: " + err.Error())
	}

	return nil
}

// db helper functions

func getBucketByTime(tx *bolt.Tx, bucketKey []byte, t time.Time,
	interval time.Duration) (*bolt.Bucket, error) {

	bigBucket := tx.Bucket(bucketKey)
	if bigBucket == nil {
		return nil, errors.New("Required bucket does not exist")
	}

	bucketTimestampKey := t.Truncate(interval)
	bucket, err := bigBucket.CreateBucketIfNotExists(timeToBytes(bucketTimestampKey))
	if err != nil {
		return nil, err
	}
	return bucket, nil
}

// encoding helper functions

func bytesToTime(b []byte) time.Time {
	t, err := time.Parse(time.RFC3339, string(b))
	if err != nil {
		log.Panicln("bytesToTime:", err)
	}
	return t
}

func timeToBytes(t time.Time) []byte {
	return []byte(t.Format(time.RFC3339))
}

func encodeUint64(n uint64) []byte {
	b := make([]byte, 8)
	binary.LittleEndian.PutUint64(b, n)
	return b
}

func decodeUint64(b []byte) uint64 {
	return binary.LittleEndian.Uint64(b)
}

func encodeBool(b bool) []byte {
	if b {
		return encodeUint64(1)
	}
	return encodeUint64(0)
}

func decodeBool(b []byte) bool {
	v := decodeUint64(b)
	return v != 0
}

func readEventFromRequestBody(req *http.Request) (*Event, error) {
	body, err := ioutil.ReadAll(req.Body)
	if err != nil {
		return nil, err
	}

	event := &Event{}
	err = json.Unmarshal(body, event)
	if err != nil {
		return nil, err
	}

	return event, nil
}

// convert database entries to Events then send
func sendGeneric(eventMap map[string][]byte, t time.Time,
	createEvent func(k string, v []byte) (*segment.Track, error)) error {

	batch := &segment.Batch{
		Messages: []interface{}{},
		Message: segment.Message{
			Context: map[string]interface{}{
				"library": map[string]string{
					"name": "analytics-go",
				},
			},
		},
	}

	// create JSON events for each map entry
	for k, v := range eventMap {
		event, err := createEvent(k, v)
		if err != nil {
			return errors.New("Failed to create event object: " + err.Error())
		}
		event.Timestamp = &t

		batch.Track(event)
	}

	// send to segment
	// TODO: make this more robust/fail-proof wrt delete
	err := segmentClient.Batch(batch)
	if err != nil {
		return errors.New("Failed to track event with segment: " + err.Error())
	}
	return nil
}

func sendEvents(eventMap map[string][]byte, t time.Time) error {
	return sendGeneric(eventMap, t, createEvent)
}

func sendUsers(eventMap map[string][]byte, t time.Time) error {
	return sendGeneric(eventMap, t, createUserEvent)
}

func sendDailyMetrics(eventMap map[string][]byte, t time.Time) error {
	return sendGeneric(eventMap, t, createDailyMetricEvent)
}

// helpers for use with sendBucket

func createEvent(key string, value []byte) (*segment.Track, error) {
	info, err := lookupEvent(key)
	if err != nil {
		return nil, errors.New("Failed to create event: " + err.Error())
	}

	event := info.Template
	event.AnonymousID = companyID
	event.Properties["Value"] = decodeUint64(value)

	return &event, nil
}

func createUserEvent(key string, value []byte) (*segment.Track, error) {
	au := "ACTIVE_USER"
	info, err := lookupEvent(au)
	if err != nil {
		return nil, errors.New("Failed to create event: " + err.Error())
	}

	event := info.Template
	event.AnonymousID = key
	event.Properties["Company Name"] = companyID

	return &event, nil
}

func createDailyMetricEvent(key string, value []byte) (*segment.Track, error) {
	info, err := lookupDailyMetric(key)
	if err != nil {
		return nil, errors.New("Event lookup failed: " + err.Error())
	}

	event := info.Template
	event.AnonymousID = companyID
	switch info.ValueType {
	case Boolean:
		event.Properties["Value"] = decodeBool(value)
	case String:
		event.Properties["Value"] = string(value)
	case Integer:
		event.Properties["Value"] = decodeUint64(value)
	default:
		return &segment.Track{}, errors.New("Failed to match event value type")
	}

	return &event, nil
}

func getOldBucketKeys(db *BoltKV, bucketKey []byte, interval time.Duration) ([][]byte, error) {
	var keys [][]byte

	// get list of old buckets to send, ignore current
	err := db.View(func(tx *bolt.Tx) error {
		eventBucket := tx.Bucket(bucketKey)
		return eventBucket.ForEach(func(k, v []byte) error {
			if bytesToTime(k) != clock.Now().Truncate(interval) {
				keys = append(keys, k)
			}
			return nil
		})
	})
	if err != nil {
		return nil, errors.New("Failed to read keys from db bucket: " + err.Error())
	}
	return keys, nil
}

// attempts to send all events
// parameters
// db: a pointer to the db object
// bucketKey: the name of the top-level bucket to send, as []byte
// interval: the proper interval to truncate to for sub-buckets
// createEvent: a function to create Event objects from db KV pairs
func sendBucket(db *BoltKV, bucketKey []byte, interval time.Duration,
	sendFunc func(map[string][]byte, time.Time) error) error {

	keys, err := getOldBucketKeys(db, bucketKey, interval)
	if err != nil {
		return errors.New("Failed to read keys from db bucket: " + err.Error())
	}

	// for each old bucket
	for _, key := range keys {
		// read all active users
		eventMap := make(map[string][]byte)
		if err := db.View(func(tx *bolt.Tx) error {
			bucket := tx.Bucket(bucketKey)
			return bucket.Bucket(key).ForEach(func(k, v []byte) error {
				// copy map because you cannot use the bolt slices
				tmp := make([]byte, len(v))
				copy(tmp, v)
				eventMap[string(k)] = tmp
				return nil
			})
		}); err != nil {
			return errors.New("Failed to read KV pairs from sub-bucket: " + err.Error())
		}

		// then try to send
		err := sendFunc(eventMap, bytesToTime(key))
		if err != nil {
			return errors.New("Failed to send events: " + err.Error())
		}

		// and delete bucket if sent successfully
		err = db.Update(func(tx *bolt.Tx) error {
			return tx.Bucket(bucketKey).DeleteBucket(key)
		})
		if err != nil {
			// duplicate events may be sent after this is hit. TODO: panic?
			return errors.New("Failed to delete sub-bucket: " + err.Error())
		}
	}
	return nil
}

func sendLoop(db *BoltKV) {
	ticker := time.NewTicker(TickerInterval)

	for range ticker.C {
		err := sendBucket(db, EventsKey, EventsInterval, sendEvents)
		if err != nil {
			log.Println("Failed to send events:", err)
		}
		err = sendBucket(db, UsersKey, UsersInterval, sendUsers)
		if err != nil {
			log.Println("Failed to send users:", err)
		}
		err = sendBucket(db, DailyMetricsKey, DailyMetricsInterval, sendDailyMetrics)
		if err != nil {
			log.Println("Failed to send daily metrics:", err)
		}
	}
}

func getDailyMetrics(db *BoltKV, httpClient ServiceHTTPClient) error {
	exists := false
	now := timeToBytes(clock.Now().Truncate(DailyMetricsInterval))
	err := db.View(func(tx *bolt.Tx) error {
		dailyMetricsBucket := tx.Bucket(DailyMetricsKey)
		currentBucket := dailyMetricsBucket.Bucket(now)
		if currentBucket != nil {
			exists = true
		}
		return nil
	})
	if err != nil {
		return errors.New("Failed to read daily metrics from db: " + err.Error())
	}

	if exists {
		return nil
	}

	var dailyMetrics map[string][]byte
	// GET DAILY METRICS HERE BY QUERYING SERVICES

	err = db.Update(func(tx *bolt.Tx) error {
		dailyMetricsBucket := tx.Bucket(DailyMetricsKey)
		currentBucket, err := dailyMetricsBucket.CreateBucket(now)
		if err != nil {
			return errors.New("Failed to create daily metrics sub-bucket: " + err.Error())
		}
		for k, v := range dailyMetrics {
			err = currentBucket.Put([]byte(k), v)
			if err != nil {
				return errors.New("Failed to write key-value pair to db: " + err.Error())
			}
		}
		return nil
	})
	if err != nil {
		return errors.New("Failed to persist daily metrics: " + err.Error())
	}
	return nil
}

// stub method for collecting and sending "daily" metrics
func dailyMetricsLoop(db *BoltKV, httpClient ServiceHTTPClient) {
	ticker := time.NewTicker(TickerInterval)

	for range ticker.C {
		err := getDailyMetrics(db, httpClient)
		if err != nil {
			log.Println("Failed to get daily metrics:", err)
		}
	}
}

func eventHandler(db *BoltKV) auth.Handle {
	return func(resp http.ResponseWriter, req *http.Request, c auth.Context) {
		// parse event from request
		event, err := readEventFromRequestBody(req)
		if err != nil {
			log.Println("Error reading request body:", err)
			http.Error(resp, "", http.StatusBadRequest)
			return
		}
		// validate event format
		if err := validateEvent(event); err != nil {
			log.Println("Failed to validate event:", err)
			http.Error(resp, "", http.StatusBadRequest)
			return
		}

		// get keys/value from EventMessage
		key := []byte(event.Event)
		userid := []byte(nil)
		if event.UserID != "" {
			// calculate hash, then convert array to slice
			sha := sha256.Sum256([]byte(event.UserID))
			userid = make([]byte, hex.EncodedLen(len(sha)))
			hex.Encode(userid, sha[:])
		}
		value := event.Value

		// increment database event counter
		err = db.Update(func(tx *bolt.Tx) error {
			// get current event & user buckets
			t := clock.Now()
			eventBucket, err := getBucketByTime(tx, EventsKey, t, EventsInterval)
			if err != nil {
				return err
			}

			// increment event counter
			vprime := value
			if v := eventBucket.Get(key); v != nil {
				vprime += decodeUint64(v)
			}

			err = eventBucket.Put(key, encodeUint64(vprime))
			if err != nil {
				return err
			}

			// record that user is active
			if userid != nil {
				userBucket, err := getBucketByTime(tx, UsersKey, t, UsersInterval)
				if err != nil {
					return err
				}
				return userBucket.Put(userid, PresentFlag)
			}
			return nil
		})
		if err != nil {
			log.Println("Failed to update db:", err)
			http.Error(resp, "", http.StatusInternalServerError)
			return
		}

		http.Error(resp, "", http.StatusOK)
		return
	}
}

func main() {
	log.Println("waiting for deps")
	service.ServiceBarrier()

	router := httprouter.New()

	// check if analytics is enabled
	config := config.NewClient("analytics")
	c, err := config.Get()
	if err != nil {
		log.Fatal("Failed to fetch config:", err)
	}

	if strings.EqualFold("true", c["analytics.enabled"]) {
		// retrieve company name from license
		companyID := c["license_company"]
		if companyID == "" {
			companyID = c["customer_id"]
		}
		if companyID == "" {
			log.Fatal("Failed to read non-empty company name")
		}

		// read db file location flag
		var dbFile string
		flag.StringVar(&dbFile, "db", "/data/analytics/db", "path to database file")
		flag.Parse()

		err = os.MkdirAll(path.Dir(dbFile), 0600)
		if err != nil {
			log.Fatal("Failed to create data dir:", err)
		}

		// create or open database file
		db, err := NewBoltKV(dbFile, setupDB)
		if err != nil {
			log.Fatal("Failed to open boltdb:", err)
		}
		defer db.Close()

		secret := service.ReadDeploymentSecret()

		// set up daily event sending
		httpClient := NewDefaultServiceHTTPClient("analytics", secret)
		go dailyMetricsLoop(db, httpClient)

		//segmentClient.Endpoint = c["analytics.endpoint"]
		// identify company by its name for Mixpanel people
		err = segmentClient.Identify(&segment.Identify{
			AnonymousID: companyID,
			Traits: map[string]interface{}{
				"Company Name": companyID,
			},
		})
		if err != nil {
			log.Println("Failed to send identify message:", err)
		}

		go sendLoop(db)

		// handle incoming events
		router.POST("/events", auth.Auth(eventHandler(db),
			auth.NewServiceSharedSecretExtractor(secret)))
	} else {
		router.POST("/",
			auth.OptionalAuth(func(resp http.ResponseWriter, req *http.Request, c auth.Context) {
				http.Error(resp, "Service disabled", http.StatusServiceUnavailable)
			}))
	}

	err = http.ListenAndServe(":9400", service.Log(router))
	if err != nil {
		log.Fatal("ListenAndServe:", err)
	}
}
