package main

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
	"time"

	"github.com/boltdb/bolt"
)

//TODO change to permanent values
const (
	eventBucketInterval = time.Second * 8
	userBucketInterval  = time.Second * 10
	sendInterval        = time.Second * 8
)

// const arrays are not possible in Go
var (
	eventBucketKey = []byte("events")
	userBucketKey  = []byte("users")

	presentFlag = []byte{}
)

// global time indirection to allow testing
type clockImpl struct{}

func (c *clockImpl) Now() time.Time {
	return time.Now()
}

var clock interface {
	Now() time.Time
} = &clockImpl{}

// BoltKV - wrapper around bolt.DB to allow future extensibility
type BoltKV struct {
	*bolt.DB
}

// create or open a new Bolt database & call setup function
func newBoltKV(filename string, setup func(*BoltKV) error) (*BoltKV, error) {
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
		_, err := tx.CreateBucketIfNotExists(eventBucketKey)
		if err != nil {
			return err
		}
		_, err = tx.CreateBucketIfNotExists(userBucketKey)
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
	return time.Unix(int64(decodeUint64(b)), 0)
}

func timeToBytes(t time.Time) []byte {
	return encodeUint64(uint64(t.Unix()))
}

func encodeUint64(n uint64) []byte {
	b := make([]byte, 8)
	binary.LittleEndian.PutUint64(b, n)
	return b
}

func decodeUint64(b []byte) uint64 {
	return binary.LittleEndian.Uint64(b)
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
	createEvent func(k string, v []byte) (*Event, error)) error {

	// create JSON events for each map entry
	for k, v := range eventMap {
		event, err := createEvent(k, v)
		if err != nil {
			return errors.New("Failed to create event object: " + err.Error())
		}
		event.Timestamp = &t
		event.CustomerID = "Customer-ID"

		eventJSON, err := json.Marshal(event)
		if err != nil {
			return err
		}
		log.Printf("Sending %s\n", eventJSON)
	}
	return nil
}

func sendEvents(eventMap map[string][]byte, t time.Time) error {
	return sendGeneric(eventMap, t, createEvent)
}

func sendUsers(eventMap map[string][]byte, t time.Time) error {
	return sendGeneric(eventMap, t, createUserEvent)
}

// helpers for use with sendBucket

func createEvent(key string, value []byte) (*Event, error) {
	event, err := lookupEvent(key)
	if err != nil {
		return nil, errors.New("Failed to create event: " + err.Error())
	}
	event.Value = decodeUint64(value)

	return &event, nil
}

func createUserEvent(key string, value []byte) (*Event, error) {
	au := "ACTIVE_USER"
	event, err := lookupEvent(au)
	if err != nil {
		return nil, errors.New("Failed to create event: " + err.Error())
	}
	event.UserID = key

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
	ticker := time.NewTicker(sendInterval)

	for range ticker.C {
		err := sendBucket(db, eventBucketKey, eventBucketInterval, sendEvents)
		if err != nil {
			log.Println("Failed to send event bucket(s):", err)
			continue
		}

		err = sendBucket(db, userBucketKey, userBucketInterval, sendUsers)
		if err != nil {
			log.Println("Failed to send user bucket(s):", err)
			continue
		}
	}
}

func eventHandler(db *BoltKV) http.HandlerFunc {
	return func(resp http.ResponseWriter, req *http.Request) {
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
			eventBucket, err := getBucketByTime(tx, eventBucketKey, t, eventBucketInterval)
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
				userBucket, err := getBucketByTime(tx, userBucketKey, t, userBucketInterval)
				if err != nil {
					return err
				}
				return userBucket.Put(userid, presentFlag)
			}
			return nil
		})
		if err != nil {
			log.Println("Failed to update db:", err)
			http.Error(resp, "", http.StatusInternalServerError)
			return
		}
	}
}

func main() {
	var dbFile string
	flag.StringVar(&dbFile, "db", "/data/analytics/db", "path to database file")
	flag.Parse()

	err := os.MkdirAll(path.Dir(dbFile), 0600)
	if err != nil {
		log.Fatal("Failed to create data dir:", err)
	}

	// create or open database file
	db, err := newBoltKV(dbFile, setupDB)
	if err != nil {
		log.Fatal("Failed to open boltdb:", err)
	}
	defer db.Close()

	go sendLoop(db)

	// handle incoming events
	http.HandleFunc("/events", eventHandler(db))

	err = http.ListenAndServe(":9400", nil)
	if err != nil {
		log.Fatal("ListenAndServe:", err)
	}
}
