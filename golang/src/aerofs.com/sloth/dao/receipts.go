package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"time"
)

func GetReceipts(tx *sql.Tx, cid string) []LastReadReceipt {
	rows, err := tx.Query("SELECT user_id,msg_id,time FROM last_read WHERE convo_id=?", cid)
	errors.PanicAndRollbackOnErr(err, tx)
	return parseReceiptRows(tx, rows)
}

func SetReceipt(tx *sql.Tx, caller, cid string, mid int64) *LastReadReceipt {
	receipt := LastReadReceipt{
		UserId:    caller,
		MessageId: mid,
		Time:      time.Now(),
	}
	_, err := tx.Exec("INSERT INTO last_read (user_id,convo_id,msg_id,time) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE msg_id=?, time=?",
		caller,
		cid,
		mid,
		receipt.Time.UnixNano(),
		mid,
		receipt.Time.UnixNano(),
	)
	errors.PanicAndRollbackOnErr(err, tx)
	return &receipt
}

func parseReceiptRows(tx *sql.Tx, rows *sql.Rows) []LastReadReceipt {
	defer rows.Close()
	receipts := make([]LastReadReceipt, 0)
	for rows.Next() {
		receipts = append(receipts, *parseReceipt(tx, rows))
	}
	return receipts
}

func parseReceipt(tx *sql.Tx, row Row) *LastReadReceipt {
	var r LastReadReceipt
	var timeUnixNanos int64
	err := row.Scan(&r.UserId, &r.MessageId, &timeUnixNanos)
	errors.PanicAndRollbackOnErr(err, tx)
	r.Time = time.Unix(0, timeUnixNanos)
	return &r
}
