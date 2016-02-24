// Some helper methods for error handling
package errors

import (
	"database/sql"
	"github.com/go-sql-driver/mysql"
	"log"
)

const FOREIGN_KEY_CONSTRAINT_FAILED = 1452
const UNIQUE_CONSTRAINT_FAILED = 1062

// Panic if the given error is not nil
func PanicOnErr(err error) {
	if err != nil {
		log.Panicf("%T %v\n", err, err)
	}
}

// Rollback a transaction and panic if the given error is not nil
func PanicAndRollbackOnErr(err error, tx *sql.Tx) {
	if err != nil && tx != nil {
		tx.Rollback()
	}
	PanicOnErr(err)
}

// RecoverAndLog, when deferred, recovers from any panic and logs
// the cause
func RecoverAndLog() {
	if r := recover(); r != nil {
		log.Print("recovered: ", r)
	}
}

// Return true iff the given error is a mysql error due to a failed unique
// constraint
func UniqueConstraintFailed(err error) bool {
	return isSpecificMysqlError(err, UNIQUE_CONSTRAINT_FAILED)
}

// Return true iff the given error is a mysql error due to a failed foreign key
// constraint
func ForeignKeyConstraintFailed(err error) bool {
	return isSpecificMysqlError(err, FOREIGN_KEY_CONSTRAINT_FAILED)
}

func isSpecificMysqlError(err error, code uint16) bool {
	if err == nil {
		return false
	}
	m, ok := err.(*mysql.MySQLError)
	return ok && m.Number == code
}
