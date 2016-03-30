package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"fmt"
	"strings"
)

func GetSettings(tx *sql.Tx, uid string) *UserSettings {
	var notifyOnlyOnTag bool
	err := tx.QueryRow("SELECT s_notify_only_on_tag FROM users WHERE id=?", uid).Scan(&notifyOnlyOnTag)
	if err == sql.ErrNoRows {
		return nil
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return &UserSettings{
		NotifyOnlyOnTag: &notifyOnlyOnTag,
	}
}

func ChangeSettings(tx *sql.Tx, uid string, p *UserSettings) {
	cols := make([]string, 0)
	args := make([]interface{}, 0)
	if p.NotifyOnlyOnTag != nil {
		cols = append(cols, "s_notify_only_on_tag=?")
		args = append(args, *p.NotifyOnlyOnTag)
	}
	// add checks for new settings here
	query := fmt.Sprint("UPDATE users SET ", strings.Join(cols, ","), " WHERE id=?")
	args = append(args, uid)
	_, err := tx.Exec(query, args...)
	errors.PanicAndRollbackOnErr(err, tx)
}
