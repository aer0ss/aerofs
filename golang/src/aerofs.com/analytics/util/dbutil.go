package util

import (
	"aerofs.com/analytics/db"
	"errors"
	"github.com/boltdb/bolt"
	"time"
)

func getOldBucketKeys(db *db.BoltKV, bucketKey []byte,
	interval time.Duration, now time.Time) ([][]byte, error) {

	var keys [][]byte

	// get list of old buckets to send, ignore current
	err := db.View(func(tx *bolt.Tx) error {
		eventBucket := tx.Bucket(bucketKey)
		return eventBucket.ForEach(func(k, v []byte) error {
			if BytesToTime(k) != now.Truncate(interval) {
				// copy map because you cannot use the bolt slices
				tmp := make([]byte, len(k))
				copy(tmp, k)
				keys = append(keys, tmp)
			}
			return nil
		})
	})
	if err != nil {
		return nil, errors.New("Failed to read keys from db bucket: " + err.Error())
	}
	return keys, nil
}

// SendBucket - sends all events in a bucket, assuming keys are timestamps
// parameters
// db: a pointer to the db object
// bucketKey: the name of the top-level bucket to send, as []byte
// interval: the proper interval to truncate to for sub-buckets
// createEvent: a function to create Event objects from db KV pairs
func SendBucket(db *db.BoltKV, bucketKey []byte, interval time.Duration, now time.Time,
	sendFunc func(map[string][]byte, time.Time) error) error {

	keys, err := getOldBucketKeys(db, bucketKey, interval, now)
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
		err := sendFunc(eventMap, BytesToTime(key))
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
