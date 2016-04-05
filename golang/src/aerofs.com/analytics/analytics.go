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

	"aerofs.com/analytics/db"
	"aerofs.com/analytics/segment"
	"aerofs.com/analytics/util"
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
	EventsInterval = time.Hour * 2
	UsersInterval  = time.Hour * 24
	TickerInterval = time.Second * 5

	SegmentWriteKey = "rwp5ZN1LQIGfcrGukEMUtUQ3QHokYJQz"
)

// const arrays are not possible in Go
var (
	companyID string
	conf      map[string]string

	EventsKey = []byte("events")
	UsersKey  = []byte("users")

	PresentFlag = []byte{}
)

var segmentClient = segment.Client{
	Client: http.DefaultClient,
	//Endpoint: "https://api.segment.io",
	Token: SegmentWriteKey,
}

var clock util.Clock = &util.DefaultClockImpl{}

// db helper functions

// initialize Bolt database
func setupDB(db *db.BoltKV) error {
	err := db.Update(func(tx *bolt.Tx) error {
		_, err := tx.CreateBucketIfNotExists(EventsKey)
		if err != nil {
			return err
		}
		_, err = tx.CreateBucketIfNotExists(UsersKey)
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

func getBucketByTime(tx *bolt.Tx, bucketKey []byte, t time.Time,
	interval time.Duration) (*bolt.Bucket, error) {

	bigBucket := tx.Bucket(bucketKey)
	if bigBucket == nil {
		return nil, errors.New("Required bucket does not exist")
	}

	bucketTimestampKey := t.Truncate(interval)
	bucket, err := bigBucket.CreateBucketIfNotExists(util.TimeToBytes(bucketTimestampKey))
	if err != nil {
		return nil, err
	}
	return bucket, nil
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
			log.Println("Failed to create event object:", err)
			continue
		}
		event.Timestamp = &t

		err = batch.Track(event)
		if err != nil {
			log.Println("Failed to track batch: ", err)
			continue
		}
	}

	if len(batch.Messages) == 0 {
		log.Println("No messages in batch")
		return nil
	}

	// send to segment
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
	event.Properties["Value"] = util.DecodeUint64(value)

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
		event.Properties["Value"] = util.DecodeBool(value)
	case String:
		event.Properties["Value"] = string(value)
	case Integer:
		event.Properties["Value"] = util.DecodeUint64(value)
	default:
		return &segment.Track{}, errors.New("Failed to match event value type")
	}

	return &event, nil
}

func sendLoop(db *db.BoltKV) {
	ticker := time.NewTicker(TickerInterval)

	for range ticker.C {
		now := clock.Now()
		err := util.SendBucket(db, EventsKey, EventsInterval, now, sendEvents)
		if err != nil {
			log.Println("Failed to send events:", err)
		}
		err = util.SendBucket(db, UsersKey, UsersInterval, now, sendUsers)
		if err != nil {
			log.Println("Failed to send users:", err)
		}
	}
}

func dailyMetricsHandler(resp http.ResponseWriter, req *http.Request, c auth.Context) {
	b, err := ioutil.ReadAll(req.Body)
	if err != nil {
		log.Println("Error reading request body:", err)
		http.Error(resp, "", http.StatusInternalServerError)
		return
	}

	var dailyMetricsMap = make(map[string][]byte)
	err = json.Unmarshal(b, &dailyMetricsMap)
	if err != nil {
		log.Println("Error parsing json:", err)
		http.Error(resp, "", http.StatusBadRequest)
		return
	}

	err = sendDailyMetrics(dailyMetricsMap, clock.Now())
	if err != nil {
		log.Println("Fafiled to send daily metrics to segment:", err)
		http.Error(resp, "", http.StatusBadGateway)
		return
	}

	http.Error(resp, "", http.StatusOK)
	return
}

func eventHandler(db *db.BoltKV) auth.Handle {
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

		log.Println(event.Event)

		// increment database event counter
		err = db.Update(func(tx *bolt.Tx) error {
			// get current event & user buckets
			t := clock.Now()
			if event.Event != "ACTIVE_USER" {
				eventBucket, err := getBucketByTime(tx, EventsKey, t, EventsInterval)
				if err != nil {
					return err
				}

				// increment event counter
				vprime := value
				if v := eventBucket.Get(key); v != nil {
					vprime += util.DecodeUint64(v)
				}

				err = eventBucket.Put(key, util.EncodeUint64(vprime))
				if err != nil {
					return err
				}
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
	var err error
	config := config.NewClient("analytics")
	conf, err = config.Get()
	if err != nil {
		log.Fatal("Failed to fetch config:", err)
	}

	if strings.EqualFold("true", conf["analytics.enabled"]) {
		// retrieve company name from license
		companyID = conf["license_company"] + " - " + conf["customer_id"]
		if companyID == " - " {
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
		db, err := db.NewBoltKV(dbFile, setupDB)
		if err != nil {
			log.Fatal("Failed to open boltdb:", err)
		}
		defer db.Close()

		segmentClient.Endpoint = conf["analytics.endpoint"]
		if segmentClient.Endpoint == "" {
			log.Println("Analytics endpoint is empty, events will not send")
		}
		// identify company by its name for Mixpanel people
		err = segmentClient.Identify(&segment.Identify{
			AnonymousID: companyID,
			Context: map[string]interface{}{
				"library": map[string]string{
					"name": "analytics-go",
				},
			},
			Traits: map[string]interface{}{
				"Company Name": companyID,
			},
		})
		if err != nil {
			log.Println("Failed to send identify message:", err)
		}

		go sendLoop(db)

		secret := service.ReadDeploymentSecret()
		// handle incoming events
		router.POST("/events", auth.Auth(eventHandler(db),
			auth.NewServiceSharedSecretExtractor(secret)))
		router.POST("/dailymetrics", auth.Auth(dailyMetricsHandler,
			auth.NewServiceSharedSecretExtractor(secret)))
	} else {
		log.Println("Analytics disabled.")
		router.POST("/*anything",
			auth.OptionalAuth(func(resp http.ResponseWriter, req *http.Request, c auth.Context) {
				http.Error(resp, "Service disabled", http.StatusServiceUnavailable)
			}))
	}

	err = http.ListenAndServe(":9400", service.Log(router))
	if err != nil {
		log.Fatal("ListenAndServe:", err)
	}
}
