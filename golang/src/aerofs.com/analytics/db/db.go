package db

import (
	"github.com/boltdb/bolt"
)

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
