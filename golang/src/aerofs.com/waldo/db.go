package main

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"github.com/boltdb/bolt"
	"io"
	"log"
	"os"
	"sync"
	"sync/atomic"
)

//
// Content Availability Database
// -----------------------------
//
// (OID, DID, version)
//
// supported operations
//   - batch insert: (did, [(oid, version)])
//   - list devices: oid -> [(did, version)]
//
// BoltDB is used for the main database as it is easy to use and offers
// excellent read and decent write performance.
//
// There is a single bucket, with the OID as key and a list of (DID, version)
// tuples as values. For now the encoding of this list is a straightforward
// sequence of 16 bytes OID followed by a varint version. The first byte of
// the value is reserved to describe the encoding, to leave room for more
// compact encodings in the future.
//
// A write-ahead-log is used to speed up insertions. For simplicity, this
// log is kept in sync with an equivalent in-memory cache. This provides
// extremely fast reads of recent writes.
// The write-ahead-log is checkpointed when the in-memory cache exceeds
// a fixed size threshold.
//
// For simplicity, the WAL is a flat sequence of fixed size entries:
// OID (16 bytes), DID (16 bytes), version (8 bytes)
//
//
// TS/SA Database
// --------------
//
// For sync status queries, it is necessary to know which devices are
// TeamServer/StorageAgent. Instead of obtaining that information from
// Sparta, this is done by maintaining a set of TS/SA DIDs in the
// database.
//
// Since cert auth is used to submit location information, the userid
// is known at submission time so it is trivial to distinguish TS/SA
// from regular devices.
//
// For performance reasons, an in-memory write-through cache is used.
// This makes membership tests extremely fast and prevents redundant
// writes to the DB.
//

type DB struct {
	// append-only Write Ahead Log
	w *os.File

	// database
	b *bolt.DB

	// in memory cache for reads
	obj        sync.RWMutex
	dirtyCount uint32
	chkSched   uint32
	dirty      map[UID]*SortedVersionMap

	// set of TS/SA DID
	// write-through cache for corresponding db bucket
	// rare writes, no WAL entries
	// TODO: lock-free set
	dev sync.Mutex
	sa  map[UID]struct{}
}

func OpenDB(dbFile string) (*DB, error) {
	w, err := os.OpenFile(dbFile+"-wal", os.O_RDWR|os.O_CREATE, 0600)
	if err != nil {
		return nil, err
	}

	b, err := bolt.Open(dbFile, 0600, nil)
	if err != nil {
		w.Close()
		return nil, err
	}

	// create buckets
	if err = b.Update(createBuckets); err != nil {
		w.Close()
		b.Close()
		return nil, err
	}

	db := &DB{
		w:     w,
		b:     b,
		dirty: make(map[UID]*SortedVersionMap),
		sa:    make(map[UID]struct{}),
	}

	// reload WAL entries in-memory
	if err = db.reloadWAL(); err != nil {
		db.Close()
		return nil, err
	}

	return db, nil
}

const (
	MaxCacheSize = 4096
)

var PRESENT struct{}
var EMPTY []byte = []byte{}
var BUCKET_SA []byte = []byte("sa")
var BUCKET_AVAIL []byte = []byte("avail")

func (db *DB) Close() {
	db.b.Close()
	db.w.Close()
}

func createBuckets(tx *bolt.Tx) error {
	if _, err := tx.CreateBucketIfNotExists(BUCKET_SA); err != nil {
		return err
	}
	if _, err := tx.CreateBucketIfNotExists(BUCKET_AVAIL); err != nil {
		return err
	}
	return nil
}

func (db *DB) reloadWAL() error {
	off, err := db.w.Seek(0, 0)
	if err != nil {
		return err
	}
	if off != 0 {
		return fmt.Errorf("failed to read WAL")
	}
	var buf [40 * 100]byte
	var n int
	for err == nil {
		n, err = db.w.Read(buf[:])
		for j := 0; j+39 < n; j += 40 {
			oid := NewUIDFromBytes(buf[j : j+16])
			did := NewUIDFromBytes(buf[j+16 : j+32])
			version := binary.LittleEndian.Uint64(buf[j+32 : j+40])
			d, err := db.dirtyVersions(oid)
			if err != nil {
				return err
			}
			//log.Println("+", oid.String(), did.String(), version)
			d.Put(did, version)
		}
		// TODO ?
		if n%40 > 0 {
			log.Println("malformed WAL:", n%40)
		}
	}
	if err == io.EOF {
		return nil
	}
	return err
}

func (db *DB) ForAllDevices(oid UID, fn func(did UID, version uint64) bool) (bool, error) {
	db.obj.RLock()
	d, present := db.dirty[oid]
	if !present {
		// TODO: 2-level cache RO + RW ?
		d = NewSortedVersionMap()
		if err := db.loadVersions(oid, d); err != nil {
			db.obj.RUnlock()
			return false, err
		}
	}
	// TODO: fine-grained synchronization
	// ideally we'd allow read/write concurrency for different objects
	r := d.ForAll(fn)
	db.obj.RUnlock()
	return r, nil
}

func (db *DB) loadVersions(oid UID, versions *SortedVersionMap) error {
	return db.b.View(func(tx *bolt.Tx) error {
		b := tx.Bucket(BUCKET_AVAIL)
		var k [16]byte
		oid.Encode(k[:])
		v := b.Get(k[:])
		if v == nil {
			return nil
		}
		t := v[0]
		if t != 0 {
			return fmt.Errorf("unsupported row encoding %d", t)
		}
		i := 1
		for i+16 < len(v) {
			dd := NewUIDFromBytes(v[i : i+16])
			dv, n := binary.Uvarint(v[i+16:])
			if n <= 0 {
				return fmt.Errorf("invalid version: %s %v %d %d", dd.String(), n, dv)
			}
			//log.Println("load:", oid.String(), dd.String(), dv)
			versions.Load(dd, dv)
			i += 16 + n
		}
		if i != len(v) {
			return fmt.Errorf("invalid versions %d != %d", i, len(v))
		}
		return nil
	})
}

func (db *DB) dirtyVersions(oid UID) (*SortedVersionMap, error) {
	d, present := db.dirty[oid]
	if !present {
		d = NewSortedVersionMap()
		if err := db.loadVersions(oid, d); err != nil {
			db.obj.Unlock()
			return nil, err
		}
		atomic.AddUint32(&db.dirtyCount, uint32(d.Size()))
		db.dirty[oid] = d
	}
	return d, nil
}

type Batch struct {
	db  *DB
	did UID
	idx int
	buf []byte
	err error
}

func (db *DB) BeginSetAvailableBatch(did UID, n int) *Batch {
	s := n
	if s > 100 {
		s = 100
	}
	b := &Batch{
		db:  db,
		did: did,
		buf: make([]byte, 0, 40*s),
	}
	for i := 0; i < s; i++ {
		did.Encode(b.buf[40*i+16 : 40*i+32])
	}
	db.obj.Lock()
	return b
}

func (b *Batch) SetAvailable(oid UID, version uint64) error {
	if b.err != nil {
		return b.err
	}
	i := b.idx
	if i+39 >= len(b.buf) {
		if err := b.flush(); err != nil {
			b.err = err
			return err
		}
		i = 0
	}
	oid.Encode(b.buf[i : i+16])
	binary.LittleEndian.PutUint64(b.buf[i+32:i+40], version)
	b.idx = i + 40

	d, err := b.db.dirtyVersions(oid)
	if err != nil {
		b.err = err
		return err
	}

	// NB: updates to cache MUST be rolled back if flush fails
	//log.Println("put:", oid.String(), b.did.String(), version)
	if d.Put(b.did, version) {
		atomic.AddUint32(&b.db.dirtyCount, 1)
	}
	return nil
}

func (b *Batch) End() error {
	err := b.flush()
	if err != nil || b.err != nil {
		// reload cache from WAL in case of mismatch
		if b.idx > 0 || b.err != nil {
			log.Printf("reload wal: %d %#v\n", b.idx, b.err)
			if err := b.db.reloadWAL(); err != nil {
				log.Panic(err)
			}
		}
		b.db.obj.Unlock()
		return err
	}
	if err := b.db.w.Sync(); err != nil {
		b.db.obj.Unlock()
		return err
	}
	if atomic.LoadUint32(&b.db.dirtyCount) > MaxCacheSize &&
		atomic.CompareAndSwapUint32(&b.db.chkSched, 0, 1) {
		go b.db.checkpoint()
	}
	b.db.obj.Unlock()
	return nil
}

func (b *Batch) flush() error {
	if b.err != nil {
		return b.err
	}
	if _, err := b.db.w.Write(b.buf[:b.idx]); err != nil {
		return err
	}
	b.idx = 0
	return nil
}

func (db *DB) checkpoint() error {
	// TODO: parallel checkpointing?
	//log.Println("checkpoint")
	db.obj.Lock()
	if err := db.b.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket(BUCKET_AVAIL)
		var kbuf [16]byte
		// TODO: iterate by ascending oid for optimally efficient bolt updates
		for oid, versions := range db.dirty {
			oid.Encode(kbuf[:])
			d := versions.d
			vbuf := make([]byte, 1+(len(d)/3)*(16+binary.MaxVarintLen64))
			j := 1
			for i := 0; i+2 < len(d); i += 3 {
				v := d[i+2]
				binary.BigEndian.PutUint64(vbuf[j:j+8], d[i])
				binary.BigEndian.PutUint64(vbuf[j+8:j+16], d[i+1])
				n := binary.PutUvarint(vbuf[j+16:], v)
				j += 16 + n
			}
			if err := b.Put(kbuf[:], vbuf[:j]); err != nil {
				return err
			}
		}
		return nil
	}); err != nil {
		db.obj.Unlock()
		log.Println("checkpoint failure:", err)
		return err
	}
	if err := db.w.Truncate(0); err != nil {
		log.Panic("failed to trunc wal", err)
	}
	if n, err := db.w.Seek(0, 0); err != nil || n != 0 {
		log.Panic("failed to seek wal", err)
	}
	if err := db.w.Sync(); err != nil {
		log.Panic("failed to sync wal", err)
	}
	db.dirtyCount = 0
	db.dirty = make(map[UID]*SortedVersionMap)
	atomic.StoreUint32(&db.chkSched, 0)
	db.obj.Unlock()
	return nil
}

func (db *DB) IsSA(did UID) bool {
	db.dev.Lock()
	_, present := db.sa[did]
	db.dev.Unlock()
	return present
}

func (db *DB) AddSA(did UID) error {
	db.dev.Lock()
	if _, present := db.sa[did]; present {
		db.dev.Unlock()
		return nil
	}
	k := make([]byte, 16)
	did.Encode(k[:])
	err := db.b.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket(BUCKET_SA)
		c := b.Cursor()
		ck, _ := c.Seek(k)
		if ck != nil && !bytes.Equal(ck, k) {
			return nil
		}
		return b.Put(k, EMPTY)
	})
	if err != nil {
		db.dev.Unlock()
		return err
	}
	db.sa[did] = PRESENT
	db.dev.Unlock()
	return nil
}
