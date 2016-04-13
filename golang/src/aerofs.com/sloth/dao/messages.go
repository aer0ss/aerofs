package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"fmt"
	"log"
	"strings"
	"time"
)

func GetMessages(tx *sql.Tx, cid string, before, after, limit int) []Message {
	var limitClause, beforeClause, afterClause, order string
	queryArgs := []interface{}{cid}

	if limit > 0 {
		limitClause = fmt.Sprint("LIMIT ", limit)
	}
	if after > 0 {
		afterClause = " AND id > ? "
		queryArgs = append(queryArgs, after)
		order = " ASC "
	} else {
		order = " DESC "
	}
	if before > 0 {
		beforeClause = " AND id < ? "
		queryArgs = append(queryArgs, before)
	}

	query := fmt.Sprint(
		"(SELECT id, time, body, from_id, to_id, is_data",
		" FROM messages",
		" WHERE to_id=?", beforeClause, afterClause,
		" ORDER BY id", order,
		limitClause,
		") ORDER BY id ASC",
	)
	rows, err := tx.Query(query, queryArgs...)
	errors.PanicAndRollbackOnErr(err, tx)
	return parseMessageRows(tx, rows)
}

func PageMessages(tx *sql.Tx, after int64, limit int) []Message {
	var limitClause string
	queryArgs := []interface{}{after}

	limitClause = fmt.Sprint("LIMIT ", limit)

	query := fmt.Sprint(
		"SELECT id, time, body, from_id, to_id, is_data",
		" FROM messages",
		" WHERE id>? ORDER BY id ASC ",
		limitClause,
	)
	rows, err := tx.Query(query, queryArgs...)
	errors.PanicAndRollbackOnErr(err, tx)
	return parseMessageRows(tx, rows)
}

func FilterMessages(tx *sql.Tx, uid string, ids []int64) map[int64]Message {
	if (len(ids) < 1) {
		return nil;
	}

	queryArgs := []interface{}{}

	for i:=0; i<len(ids); i++ {
		queryArgs = append(queryArgs, ids[i])
	}

	queryArgs = append(queryArgs, uid)

	query := fmt.Sprint(
		"SELECT id, time, body, from_id, to_id, is_data",
		" FROM messages",
		" WHERE id IN (?" + strings.Repeat(",?", len(ids)-1) + ")",
		" AND to_id IN (SELECT convo_id from convo_members where user_id=?)",
	)

	log.Printf("%v, %v\n", query, uid)

	rows, err := tx.Query(query, queryArgs...)
	errors.PanicAndRollbackOnErr(err, tx)
	return mapMessageRows(tx, rows)
}

func MessageExists(tx *sql.Tx, mid int64, cid string) bool {
	err := tx.QueryRow("SELECT 1 FROM messages WHERE id=? AND to_id=?", mid, cid).Scan(new(int))
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

func mapMessageRows(tx *sql.Tx, rows *sql.Rows) map[int64]Message {
	defer rows.Close()
	messages := make(map[int64]Message, 0)
	for rows.Next() {
		message := *parseMessage(tx, rows)
		messages[message.Id] = message
	}
	return messages
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
