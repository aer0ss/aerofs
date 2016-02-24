package dao

import (
	"aerofs.com/sloth/errors"
	"database/sql"
)

func GetAclEpoch(tx *sql.Tx) (uint64, bool) {
	var epoch sql.NullInt64
	err := tx.QueryRow("SELECT acl FROM epochs").Scan(&epoch)
	errors.PanicAndRollbackOnErr(err, tx)
	return uint64(epoch.Int64), epoch.Valid
}

func SetAclEpoch(tx *sql.Tx, epoch uint64) {
	_, err := tx.Exec("UPDATE epochs SET acl=?", epoch)
	errors.PanicAndRollbackOnErr(err, tx)
}
