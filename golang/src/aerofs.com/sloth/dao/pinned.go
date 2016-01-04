package dao

import (
	"aerofs.com/sloth/errors"
	"database/sql"
)

func GetPinned(tx *sql.Tx, uid string) []string {
	cids := make([]string, 0)
	rows, err := tx.Query("SELECT convo_id FROM pinned WHERE user_id=?", uid)
	errors.PanicAndRollbackOnErr(err, tx)
	defer rows.Close()
	for rows.Next() {
		cids = append(cids, parseString(tx, rows))
	}
	return cids
}

func SetPinned(tx *sql.Tx, uid, cid string) {
	_, err := tx.Exec("INSERT INTO pinned (user_id,convo_id) VALUES (?,?)", uid, cid)
	if !errors.UniqueConstraintFailed(err) {
		errors.PanicAndRollbackOnErr(err, tx)
	}
}

func SetUnpinned(tx *sql.Tx, uid, cid string) {
	_, err := tx.Exec("DELETE FROM pinned WHERE user_id=? AND convo_id=?", uid, cid)
	errors.PanicAndRollbackOnErr(err, tx)
}

func parseString(tx *sql.Tx, row Row) string {
	var s string
	err := row.Scan(&s)
	errors.PanicAndRollbackOnErr(err, tx)
	return s
}
