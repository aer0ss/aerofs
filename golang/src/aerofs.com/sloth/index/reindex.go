package index

import (
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"database/sql"
	"io/ioutil"
	"log"
	"os"
	"strconv"
	"time"
	"aerofs.com/sloth/lastOnline"
)

// Limits the number of new messages that will be loaded into memory from mysql and indexed in one batch
const MAX_BATCH_SIZE = 100

// The purpose of this job is to find a balance between a real-time search experience for users,
// while also taking advantage of BoltDB's batch update performance when possible.
// The current solution is to poll the messages table every 2 seconds and batch update the index, rather
// than updating it once per new message.  This results in improved indexing performance and a small
// enough window of unindexed messages that users shouldn't notice the delay.
// Ensures messages are indexed exactly once by storing the last indexed message id, updating it after a
// successful batch update.
// TODO: Use a WAL instead of hitting the db every 2 seconds.  Perhaps also perform a linear scan
// of the WAL when searching the index, although merging the results of the WAL scan with those of the
// index search may be tricky.
func (idx *Index) UpdateIndexJob(db *sql.DB, lastOnlineTimes *lastOnline.Times) {
	idx.initIndex(db, lastOnlineTimes)

	ticker := time.NewTicker(2 * time.Second)

	var i int64 = -1
	indexed := &i
	for range ticker.C {
		idx.UpdateIndex(db, indexed)
	}
}

func (idx *Index) UpdateIndex(db *sql.DB, indexed *int64) {
	for {
		// only check the filesystem if we don't have the last updated index in memory
		if *indexed < 0 {
			if _, err := os.Stat(idx.IndexedFilename); err == nil {
				bytes, err := ioutil.ReadFile(idx.IndexedFilename)
				errors.PanicOnErr(err)
				*indexed, err = strconv.ParseInt(string(bytes), 10, 64)
				log.Printf("read indexed=%v from file\n", *indexed)
				errors.PanicOnErr(err)
			}
		}
		log.Printf("Check if need to update index, indexed=%v", *indexed)
		tx := dao.BeginOrPanic(db)
		messages := dao.PageMessages(tx, *indexed, MAX_BATCH_SIZE)
		dao.CommitOrPanic(tx)

		if (len(messages) == 0) {
			break
		}

		*indexed = idx.BatchIndexMessages(messages, *indexed)

		log.Printf("Updated index, last id = %v\n", *indexed)

		// update the file with the last indexed message
		ioutil.WriteFile(idx.IndexedFilename, []byte(strconv.FormatInt(*indexed, 10)), 0644)
	}
}

// TODO: this can probably be removed since it should only be hit by our instance of Eyja
func (idx *Index) initIndex(db *sql.DB, lastOnlineTimes *lastOnline.Times) {
	if _, err := os.Stat(idx.InitDoneFilename); err == nil {
		return
	}

	batch := (*idx).Index.NewBatch()

	tx := dao.BeginOrPanic(db)
	users := dao.GetAllUsers(tx, lastOnlineTimes)
	dao.CommitOrPanic(tx)

	for _, user := range users {
		err := batch.Index(indexKey(user.Id, USER),
			[]string{user.Id, user.TagId, user.FirstName, user.LastName})
		errors.PanicOnErr(err)
	}

	tx = dao.BeginOrPanic(db)
	convos := dao.GetAllConvos(tx, "")
	dao.CommitOrPanic(tx)

	for _, convo := range convos {
		if !convo.IsPublic {
			continue
		}
		err := batch.Index(indexKey(convo.Id, CONVO), convoValue(convo))
		errors.PanicOnErr(err)
	}

	err := (*idx).Index.Batch(batch)
	errors.PanicOnErr(err)
	ioutil.WriteFile(idx.InitDoneFilename, []byte("done"), 0644)
}