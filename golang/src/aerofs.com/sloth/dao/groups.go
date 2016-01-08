package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"aerofs.com/sloth/util"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"
)

type stringSet map[string]struct{}

//
// Public
//

// FIXME: cleanup (maybe remove the join and do multiple simple queries?)
func GetAllGroups(tx *sql.Tx, caller string) []Group {
	groups := make(map[string]Group)
	// query db
	rows, err := tx.Query(fmt.Sprint(
		"SELECT id,created_time,name,is_public,user_id ",
		"FROM groups ",
		"LEFT JOIN group_members ON id=group_id ",
		"WHERE id IN (SELECT group_id from group_members WHERE user_id=?) ",
		"OR id IN (SELECT id from groups WHERE is_public=1) ",
	), caller)
	errors.PanicOnErr(err)
	defer rows.Close()
	// build a map of gid -> group
	for rows.Next() {
		var uid sql.NullString
		var gid, name string
		var createdTimeUnixNano int64
		var isPublic bool
		err := rows.Scan(&gid, &createdTimeUnixNano, &name, &isPublic, &uid)
		errors.PanicOnErr(err)
		group, ok := groups[gid]
		if ok {
			group.AddMember(uid.String)
		} else {
			group = Group{
				Id:          gid,
				CreatedTime: time.Unix(0, createdTimeUnixNano),
				Name:        name,
				IsPublic:    isPublic,
				Members:     make([]string, 0),
			}
			// N.B. LEFT JOIN sets uid to NULL if the group has no members
			if uid.Valid {
				group.AddMember(uid.String)
			}
		}
		// necessary because go doesn't allow assigning to map value
		// https://code.google.com/p/go/issues/detail?id=3117
		groups[gid] = group
	}
	return values(groups)
}

func GroupExists(tx *sql.Tx, gid string) bool {
	err := tx.QueryRow("SELECT 1 FROM groups WHERE id=?", gid).Scan(new(int))
	if err == sql.ErrNoRows {
		return false
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return true
}

func GetGroup(tx *sql.Tx, gid string) *Group {
	group := Group{Id: gid}
	var unixTimeNanos int64

	err := tx.QueryRow("SELECT created_time,name,is_public FROM groups WHERE id=?", gid).
		Scan(&unixTimeNanos, &group.Name, &group.IsPublic)
	if err == sql.ErrNoRows {
		return nil
	}
	errors.PanicAndRollbackOnErr(err, tx)
	group.CreatedTime = time.Unix(0, unixTimeNanos)
	group.Members = getGroupMembers(tx, gid)

	return &group
}

func CreateGroup(tx *sql.Tx, params *GroupWritable, caller string) *Group {
	group := &Group{
		Id:          util.GenerateRandomId(),
		CreatedTime: time.Now(),
		Name:        *params.Name,
		Members:     params.Members,
		IsPublic:    *params.IsPublic,
	}
	_, err := tx.Exec("INSERT INTO groups (id,created_time,name,is_public) VALUES (?,?,?,?)", group.Id, group.CreatedTime.UnixNano(), group.Name, group.IsPublic)
	errors.PanicAndRollbackOnErr(err, tx)
	for _, uid := range group.Members {
		AddGroupMember(tx, group.Id, uid)
		InsertGroupMemberAddedMessage(tx, group.Id, uid, caller, group.CreatedTime)
	}
	return group
}

// Patches the given group with whatever params are present in the
// GroupWritable, and updates the db.
// Returns the modified group
// N.B. does not check for the group's existence!
func UpdateGroup(tx *sql.Tx, group *Group, params *GroupWritable, caller string) *Group {
	var membersAdded, membersRemoved stringSet
	// patch group with params
	if params.Name != nil {
		group.Name = *params.Name
	}
	if params.IsPublic != nil {
		group.IsPublic = *params.IsPublic
	}
	if params.Members != nil {
		membersAdded = difference(params.Members, group.Members)
		membersRemoved = difference(group.Members, params.Members)
		group.Members = params.Members
	}
	// update groups table
	_, err := tx.Exec("UPDATE groups SET name=?,is_public=? WHERE id=?",
		group.Name,
		group.IsPublic,
		group.Id,
	)
	errors.PanicAndRollbackOnErr(err, tx)
	// exit early if no changes made to membership
	if params.Members == nil || (len(membersAdded) == 0 && len(membersRemoved) == 0) {
		return group
	}
	// update group_members and group_member_history tables
	updateTime := time.Now()
	for uid := range membersRemoved {
		RemoveGroupMember(tx, group.Id, uid)
		InsertGroupMemberRemovedMessage(tx, group.Id, uid, caller, updateTime)
	}
	for uid := range membersAdded {
		AddGroupMember(tx, group.Id, uid)
		InsertGroupMemberAddedMessage(tx, group.Id, uid, caller, updateTime)
	}
	// return updated group
	return group
}

func AddGroupMember(tx *sql.Tx, gid, uid string) {
	_, err := tx.Exec("INSERT INTO group_members (group_id, user_id) VALUES (?,?)", gid, uid)
	if errors.UniqueConstraintFailed(err) {
		// idempotent adds are OK
		return
	}
	errors.PanicAndRollbackOnErr(err, tx)
}

func RemoveGroupMember(tx *sql.Tx, gid, uid string) {
	_, err := tx.Exec("DELETE FROM group_members WHERE group_id=? AND user_id=?", gid, uid)
	errors.PanicAndRollbackOnErr(err, tx)
}

func InsertGroupMemberAddedMessage(tx *sql.Tx, gid, uid, caller string, time time.Time) {
	insertGroupMemberChangeMessage(tx, gid, uid, caller, "MEMBER_ADDED", time)
}

func InsertGroupMemberRemovedMessage(tx *sql.Tx, gid, uid, caller string, time time.Time) {
	insertGroupMemberChangeMessage(tx, gid, uid, caller, "MEMBER_REMOVED", time)
}

//
// Private
//

func insertGroupMemberChangeMessage(tx *sql.Tx, gid, uid, caller, mtype string, time time.Time) {
	bytes, _ := json.Marshal(map[string]interface{}{
		"type":   mtype,
		"userId": uid,
	})
	InsertMessage(tx, &Message{
		Time:   time,
		From:   caller,
		To:     gid,
		Body:   string(bytes),
		IsData: true,
	})
}

func getGroupMembers(tx *sql.Tx, gid string) []string {
	members := make([]string, 0)
	rows, err := tx.Query("SELECT user_id FROM group_members WHERE group_id=?", gid)
	errors.PanicAndRollbackOnErr(err, tx)
	defer rows.Close()
	for rows.Next() {
		members = append(members, parseString(tx, rows))
	}
	return members
}

func difference(a, b []string) stringSet {
	s := sliceToSet(a)
	for _, v := range b {
		delete(s, v)
	}
	return s
}

func sliceToSet(s []string) stringSet {
	m := make(stringSet)
	for _, v := range s {
		m[v] = struct{}{}
	}
	return m
}

// get the values of a map as a slice
// no generics because NO GENERICS!
func values(m map[string]Group) []Group {
	slice := make([]Group, 0, len(m))
	for _, val := range m {
		slice = append(slice, val)
	}
	return slice
}
