package dao

import (
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/lastOnline"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"time"
)

const QUERY_COLUMNS = "id, first_name, last_name, tag_id, phone, ISNULL(avatar)"

func UserExists(tx *sql.Tx, uid string) bool {
	err := tx.QueryRow("SELECT 1 FROM users WHERE id=?", uid).Scan(new(int))
	if err == sql.ErrNoRows {
		return false
	}
	errors.PanicOnErr(err)
	return true
}

func GetAllUsers(tx *sql.Tx, lastOnlineTimes *lastOnline.Times) []User {
	list := make([]User, 0)
	rows, err := tx.Query("SELECT " + QUERY_COLUMNS + " FROM users")
	errors.PanicAndRollbackOnErr(err, tx)
	defer rows.Close()
	now := time.Now()
	for rows.Next() {
		u := parseUserRow(tx, rows)
		u.LastOnlineTime = lastOnlineTimes.GetElapsed(u.Id, now)
		list = append(list, *u)
	}
	return list
}

func GetUser(tx *sql.Tx, uid string, lastOnlineTimes *lastOnline.Times) *User {
	row := tx.QueryRow("SELECT "+QUERY_COLUMNS+" FROM users WHERE id=?", uid)
	user := parseUserRow(tx, row)
	if user != nil {
		user.LastOnlineTime = lastOnlineTimes.GetElapsed(uid, time.Now())
	}
	return user
}

func InsertUser(tx *sql.Tx, user *User) error {
	_, err := tx.Exec("INSERT INTO users (id, first_name, last_name, tag_id, phone) VALUES (?,?,?,?,?)",
		user.Id, user.FirstName, user.LastName, user.TagId, user.Phone,
	)
	return err
}

func UpdateUser(tx *sql.Tx, user *User) error {
	_, err := tx.Exec("UPDATE users SET id=?, first_name=?, last_name=?, tag_id=?, phone=? WHERE id=?",
		user.Id, user.FirstName, user.LastName, user.TagId, user.Phone, user.Id,
	)
	return err
}

func GetAvatar(tx *sql.Tx, uid string) []byte {
	var avatar []byte
	err := tx.QueryRow("SELECT avatar FROM users WHERE id=?", uid).Scan(&avatar)
	if err == sql.ErrNoRows || len(avatar) == 0 {
		return nil
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return avatar
}

func UpdateAvatar(tx *sql.Tx, uid string, bytes []byte) {
	_, err := tx.Exec("UPDATE users SET avatar=? WHERE id=?", bytes, uid)
	errors.PanicAndRollbackOnErr(err, tx)
}

func parseUserRow(tx *sql.Tx, row Row) *User {
	var user User
	var tagId, phone sql.NullString
	var hasNoAvatar bool
	err := row.Scan(&user.Id, &user.FirstName, &user.LastName, &tagId, &phone, &hasNoAvatar)
	if err == sql.ErrNoRows {
		return nil
	}
	errors.PanicAndRollbackOnErr(err, tx)

	if val, err := tagId.Value(); err != nil {
		errors.PanicAndRollbackOnErr(err, tx)
	} else if val != nil {
		user.TagId = val.(string)
	}

	if val, err := phone.Value(); err != nil {
		errors.PanicAndRollbackOnErr(err, tx)
	} else if val != nil {
		user.Phone = val.(string)
	}

	if !hasNoAvatar {
		user.AvatarPath = "/users/" + user.Id + "/avatar"
	}

	return &user
}
