package dao

import (
	"aerofs.com/sloth/errors"
	"database/sql"
)

// *sql.Row or *sql.Rows
type Row interface {
	Scan(...interface{}) error
}

// CommitOrPanic commits a given transaction, and panics if this returns an error
func CommitOrPanic(tx *sql.Tx) {
	err := tx.Commit()
	errors.PanicOnErr(err)
}

// BeginOrPanic begins and returns a transaction, and panics if this returns an error
func BeginOrPanic(db *sql.DB) *sql.Tx {
	tx, err := db.Begin()
	errors.PanicOnErr(err)
	return tx
}
