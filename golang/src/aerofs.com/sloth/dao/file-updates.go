package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"fmt"
	"time"
)

// return -1 if transforms from this store have never been fetched
func GetLastLogicalTimestamp(tx *sql.Tx, sid string) int64 {
	var val sql.NullInt64
	err := tx.QueryRow("SELECT last_logical_timestamp FROM stores WHERE id=?", hexDecode(tx, sid)).Scan(&val)
	if err == sql.ErrNoRows {
		return -1
	}
	errors.PanicAndRollbackOnErr(err, tx)
	if !val.Valid {
		return -1
	}
	return val.Int64
}

func SetLastLogicalTimestamp(tx *sql.Tx, sid string, timestamp int64) {
	_, err := tx.Exec(fmt.Sprint(
		"INSERT INTO stores(id, last_logical_timestamp) ",
		"VALUES (?,?) ",
		"ON DUPLICATE KEY UPDATE last_logical_timestamp=?",
	), hexDecode(tx, sid), timestamp, timestamp)
	errors.PanicAndRollbackOnErr(err, tx)
}

func InsertFileUpdateMessage(tx *sql.Tx, sid, uid, json string) {
	cid := getCidForSid(tx, sid)
	InsertMessage(tx, &Message{
		Time:   time.Now(),
		Body:   json,
		From:   uid,
		To:     cid,
		IsData: true,
	})
}
