package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"time"
)

func GetUserMessages(tx *sql.Tx, from, to string) []Message {
	rows, err := tx.Query("SELECT id,time,body,from_id,to_id,is_data FROM messages WHERE (from_id=? AND to_id=?) OR (from_id=? AND to_id=?) ORDER BY time", from, to, to, from)
	errors.PanicAndRollbackOnErr(err, tx)
	return parseMessageRows(tx, rows)
}

func GetGroupMessages(tx *sql.Tx, gid string) []Message {
	rows, err := tx.Query("SELECT id,time,body,from_id,to_id,is_data FROM messages WHERE to_id=? ORDER BY time", gid)
	errors.PanicAndRollbackOnErr(err, tx)
	return parseMessageRows(tx, rows)
}

func UserMessageExists(tx *sql.Tx, mid int64, from, to string) bool {
	err := tx.QueryRow("SELECT 1 FROM messages WHERE id=? AND ((from_id=? AND to_id=?) OR (from_id=? AND to_id=?))",
		mid, from, to, to, from,
	).Scan(new(int))

	if err == sql.ErrNoRows {
		return false
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return true
}

func GroupMessageExists(tx *sql.Tx, mid int64, gid string) bool {
	err := tx.QueryRow("SELECT 1 FROM messages WHERE id=? AND to_id=?", mid, gid).Scan(new(int))

	if err == sql.ErrNoRows {
		return false
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return true
}

// Returns the newly created message (with id)
func InsertMessage(tx *sql.Tx, msg *Message) *Message {
	res, err := tx.Exec("INSERT INTO messages (time,body,from_id,to_id,is_data) VALUES (?,?,?,?,?)",
		msg.Time.UnixNano(),
		msg.Body,
		msg.From,
		msg.To,
		msg.IsData)
	errors.PanicAndRollbackOnErr(err, tx)
	msg.Id, err = res.LastInsertId()
	errors.PanicAndRollbackOnErr(err, tx)
	return msg
}

func parseMessageRows(tx *sql.Tx, rows *sql.Rows) []Message {
	defer rows.Close()
	messages := make([]Message, 0)
	for rows.Next() {
		messages = append(messages, *parseMessage(tx, rows))
	}
	return messages
}

func parseMessage(tx *sql.Tx, row Row) *Message {
	var msg Message
	var timeUnixNanos int64
	err := row.Scan(&msg.Id, &timeUnixNanos, &msg.Body, &msg.From, &msg.To, &msg.IsData)
	errors.PanicAndRollbackOnErr(err, tx)
	msg.Time = time.Unix(0, timeUnixNanos)
	return &msg
}
