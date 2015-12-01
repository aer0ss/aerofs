package main

import (
	"aerofs.com/sloth/errors"
	"database/sql"
)

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
