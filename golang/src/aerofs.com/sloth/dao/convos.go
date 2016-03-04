package dao

import (
	"aerofs.com/sloth/errors"
	. "aerofs.com/sloth/structs"
	"aerofs.com/sloth/util"
	"aerofs.com/sloth/util/set"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"time"
)

const CHANNEL = 1
const DIRECT = 2

const CONVO_QUERY_COLS = "id, type, name, sid, is_public, created_time"

//
// Public
//

// GetAllConvos returns the all public convos and all convos of which the
// caller is a member. It returns a map of convo id to Convo struct.
func GetAllConvos(tx *sql.Tx, caller string) map[string]Convo {
	convos := make(map[string]Convo)

	// query convos table
	rows, err := tx.Query(fmt.Sprint(
		"SELECT ", CONVO_QUERY_COLS, " FROM convos WHERE ",
		"id IN (SELECT id FROM convos WHERE is_public=1) OR ",
		"id IN (SELECT convo_id FROM convo_members WHERE user_id=?)",
	), caller)
	errors.PanicAndRollbackOnErr(err, tx)
	for rows.Next() {
		c := parseConvoRow(tx, rows)
		convos[c.Id] = *c
	}
	rows.Close()

	// query pinned table
	rows, err = tx.Query("SELECT convo_id FROM pinned WHERE user_id=?", caller)
	errors.PanicAndRollbackOnErr(err, tx)
	for rows.Next() {
		var cid string
		err := rows.Scan(&cid)
		errors.PanicAndRollbackOnErr(err, tx)
		c := convos[cid]
		c.IsPinned = true
		convos[cid] = c
	}
	rows.Close()

	// query members table
	rows, err = tx.Query(fmt.Sprint(
		"SELECT user_id, convo_id FROM convo_members WHERE convo_id IN ",
		"(SELECT convo_id FROM convo_members WHERE user_id=?)",
	), caller)
	errors.PanicAndRollbackOnErr(err, tx)
	for rows.Next() {
		var uid, cid string
		err := rows.Scan(&uid, &cid)
		errors.PanicAndRollbackOnErr(err, tx)
		members := convos[cid].Members
		members = append(members, uid)
		c := convos[cid]
		c.Members = members
		convos[cid] = c
	}
	rows.Close()

	// Retrieve all bots in user's conversations
	rows, err = tx.Query(fmt.Sprint(
		"SELECT id, convo_id FROM bots WHERE convo_id IN",
		"(SELECT convo_id FROM convo_members WHERE user_id=?)",
	), caller)
	for rows.Next() {
		var bid, cid string
		err := rows.Scan(&bid, &cid)
		errors.PanicAndRollbackOnErr(err, tx)
		bots := convos[cid].Bots
		bots = append(bots, bid)
		convo := convos[cid]
		convo.Bots = bots
		convos[cid] = convo
	}
	rows.Close()

	// query receipts table
	rows, err = tx.Query(fmt.Sprint(
		"SELECT user_id, convo_id, msg_id, time FROM last_read WHERE convo_id IN ",
		"(SELECT convo_id FROM convo_members WHERE user_id=?)",
	), caller)
	errors.PanicAndRollbackOnErr(err, tx)
	for rows.Next() {
		var uid, cid string
		var mid, timeNanos int64
		err := rows.Scan(&uid, &cid, &mid, &timeNanos)
		errors.PanicAndRollbackOnErr(err, tx)
		convos[cid].Receipts[uid] = mid
	}
	rows.Close()

	return convos
}

func ConvoExists(tx *sql.Tx, cid string) bool {
	err := tx.QueryRow("SELECT 1 FROM convos WHERE id=?", cid).Scan(new(int))
	if err == sql.ErrNoRows {
		return false
	}
	errors.PanicAndRollbackOnErr(err, tx)
	return true
}

func GetConvo(tx *sql.Tx, cid, caller string) *Convo {
	// query convos table
	row := tx.QueryRow(fmt.Sprint(
		"SELECT ", CONVO_QUERY_COLS, " FROM convos WHERE id=?",
	), cid)
	c := parseConvoRow(tx, row)
	if c == nil {
		return nil
	}

	// query pinned table
	c.IsPinned = isPinned(tx, cid, caller)

	// query members table
	members := GetMembers(tx, cid)
	c.Members = members

	// query receipts table
	rows, err := tx.Query("SELECT user_id, msg_id, time FROM last_read WHERE convo_id=?", cid)
	errors.PanicAndRollbackOnErr(err, tx)
	for rows.Next() {
		var uid string
		var mid, timeNanos int64
		err := rows.Scan(&uid, &mid, &timeNanos)
		errors.PanicAndRollbackOnErr(err, tx)
		c.Receipts[uid] = mid
	}
	rows.Close()

	return c
}

func GetDirectConvo(tx *sql.Tx, members []string, caller string) *Convo {
	cid := util.GenerateDirectConvoId(members)
	return GetConvo(tx, cid, caller)
}

func CreateGroupConvo(tx *sql.Tx, p *GroupConvoWritable, caller string) *Convo {
	c := &Convo{
		Id:          util.GenerateChannelId(),
		Type:        "CHANNEL",
		CreatedTime: time.Now(),
		Name:        *p.Name,
		IsPublic:    *p.IsPublic,
		Members:     p.Members,
	}
	insertNewConvo(tx, c, CHANNEL)
	for _, uid := range c.Members {
		InsertMember(tx, c.Id, uid)
		InsertMemberAddedMessage(tx, c.Id, uid, caller, c.CreatedTime)
	}
	return c
}

func CreateDirectConvo(tx *sql.Tx, p *DirectConvoWritable) *Convo {
	c := &Convo{
		Id:          util.GenerateDirectConvoId(p.Members),
		Type:        "DIRECT",
		CreatedTime: time.Now(),
		Members:     p.Members,
	}
	insertNewConvo(tx, c, DIRECT)
	for _, uid := range c.Members {
		InsertMember(tx, c.Id, uid)
	}
	return c
}

func UpdateConvo(tx *sql.Tx, c *Convo, p *GroupConvoWritable, caller string) *Convo {
	var updated Convo = *c
	if p.Name != nil && c.Name != *p.Name {
		updated.Name = *p.Name
		_, err := tx.Exec("UPDATE convos SET name=? WHERE id=?", *p.Name, c.Id)
		errors.PanicAndRollbackOnErr(err, tx)
	}
	if p.IsPublic != nil && c.IsPublic != *p.IsPublic {
		updated.IsPublic = *p.IsPublic
		_, err := tx.Exec("UPDATE convos SET is_public=? WHERE id=?", *p.IsPublic, c.Id)
		errors.PanicAndRollbackOnErr(err, tx)
	}

	// early exit if no changes to membership
	if p.Members == nil {
		return &updated
	}
	updated.Members = p.Members
	oldMembers := set.From(c.Members)
	newMembers := set.From(p.Members)
	added := newMembers.Diff(oldMembers)
	removed := oldMembers.Diff(newMembers)
	now := time.Now()
	for uid := range removed {
		RemoveMember(tx, c.Id, uid)
		InsertMemberRemovedMessage(tx, c.Id, uid, caller, now)
	}
	for uid := range added {
		InsertMember(tx, c.Id, uid)
		InsertMemberAddedMessage(tx, c.Id, uid, caller, now)
	}
	return &updated
}

// GetAllStoreMembership returns a map of SID -> Set<UID> for all store-bound
// conversations.
func GetAllStoreMembership(tx *sql.Tx) map[string]set.Set {
	query := fmt.Sprint(
		"SELECT LOWER(HEX(sid)), user_id FROM convos INNER JOIN convo_members ",
		"ON convos.id = convo_id ",
		"WHERE sid IS NOT NULL",
	)
	rows, err := tx.Query(query)
	if err != nil {
		errors.PanicAndRollbackOnErr(err, tx)
	}
	defer rows.Close()

	members := make(map[string]set.Set)

	for rows.Next() {
		var sid, uid string
		err := rows.Scan(&sid, &uid)
		if err != nil {
			errors.PanicAndRollbackOnErr(err, tx)
		}
		m, ok := members[sid]
		if !ok {
			m = set.New()
		}
		m.Add(uid)
		members[sid] = m
	}
	return members
}

func GetMembers(tx *sql.Tx, cid string) []string {
	rows, err := tx.Query("SELECT user_id FROM convo_members WHERE convo_id=?", cid)
	errors.PanicAndRollbackOnErr(err, tx)
	defer rows.Close()
	var members []string
	for rows.Next() {
		var uid string
		err := rows.Scan(&uid)
		errors.PanicAndRollbackOnErr(err, tx)
		members = append(members, uid)
	}
	return members
}

func InsertMember(tx *sql.Tx, cid, uid string) {
	_, err := tx.Exec("INSERT INTO convo_members (convo_id, user_id) VALUES (?,?)", cid, uid)
	if errors.UniqueConstraintFailed(err) {
		// idempotent adds are OK
		return
	}
	errors.PanicAndRollbackOnErr(err, tx)
}

func RemoveMember(tx *sql.Tx, cid, uid string) {
	_, err := tx.Exec("DELETE FROM convo_members WHERE convo_id=? AND user_id=?", cid, uid)
	errors.PanicAndRollbackOnErr(err, tx)
}

func InsertMemberAddedMessage(tx *sql.Tx, cid, uid, caller string, time time.Time) {
	insertMemberChangeMessage(tx, cid, uid, caller, "MEMBER_ADDED", time)
}

func InsertMemberRemovedMessage(tx *sql.Tx, cid, uid, caller string, time time.Time) {
	insertMemberChangeMessage(tx, cid, uid, caller, "MEMBER_REMOVED", time)
}

// GetGroupSids returns the sids of all group convos bound to a shared folder
func GetGroupSids(tx *sql.Tx) []string {
	rows, err := tx.Query("SELECT LOWER(HEX(sid)) FROM convos WHERE sid IS NOT NULL AND type=?", CHANNEL)
	defer rows.Close()
	errors.PanicAndRollbackOnErr(err, tx)
	var sids []string
	for rows.Next() {
		var sid string
		err := rows.Scan(&sid)
		errors.PanicAndRollbackOnErr(err, tx)
		sids = append(sids, sid)
	}
	return sids
}

// GetCidForSid returns the convo id for the convo bound to store `sid`.
// this function panics unless there exists exactly one matching convo.
func GetCidForSid(tx *sql.Tx, sid string) string {
	var cid string
	err := tx.QueryRow("SELECT id FROM convos WHERE sid=?", hexDecode(tx, sid)).Scan(&cid)
	errors.PanicAndRollbackOnErr(err, tx)
	return cid
}

func SetConvoSid(tx *sql.Tx, cid, sid string) {
	bytes := hexDecode(tx, sid)
	_, err := tx.Exec("UPDATE convos SET sid=? WHERE id=?", bytes, cid)
	errors.PanicAndRollbackOnErr(err, tx)
}

//
// Private
//

// Returns a Convo with info from the `convos` table.
// Initializes members, receipts, etc. with empty maps/slices, but does not fill them!
func parseConvoRow(tx *sql.Tx, row Row) *Convo {
	var c Convo
	var timeNanos int64
	var ctype int
	var sid []byte
	err := row.Scan(&c.Id, &ctype, &c.Name, &sid, &c.IsPublic, &timeNanos)
	if err == sql.ErrNoRows {
		return nil
	}
	errors.PanicAndRollbackOnErr(err, tx)
	if sid != nil {
		c.Sid = hex.EncodeToString(sid)
	}
	c.Type = toTypeString(ctype)
	c.CreatedTime = time.Unix(0, timeNanos)
	c.Members = make([]string, 0)
	c.Receipts = make(map[string]int64)
	return &c
}

func isPinned(tx *sql.Tx, cid, uid string) bool {
	err := tx.QueryRow("SELECT 1 FROM pinned WHERE user_id=? AND convo_id=?", uid, cid).Scan(new(int))
	if err == nil {
		return true
	} else if err != sql.ErrNoRows {
		errors.PanicAndRollbackOnErr(err, tx)
	}
	return false
}

func insertNewConvo(tx *sql.Tx, c *Convo, ctype int) {
	sid := hexDecode(tx, c.Sid)
	_, err := tx.Exec("INSERT INTO convos (id, type, created_time, name, is_public, sid) VALUES (?,?,?,?,?,?)",
		c.Id, ctype, c.CreatedTime.UnixNano(), c.Name, c.IsPublic, sid)
	if ctype == DIRECT && errors.UniqueConstraintFailed(err) {
		return
	}
	errors.PanicAndRollbackOnErr(err, tx)
}

func insertMemberChangeMessage(tx *sql.Tx, cid, uid, caller, mtype string, time time.Time) {
	bytes, _ := json.Marshal(map[string]interface{}{
		"type":   mtype,
		"userId": uid,
	})
	InsertMessage(tx, &Message{
		Time:   time,
		From:   caller,
		To:     cid,
		Body:   string(bytes),
		IsData: true,
	})
}

func getMembers(tx *sql.Tx, cid string) []string {
	members := make([]string, 0)
	rows, err := tx.Query("SELECT user_id FROM convo_members WHERE group_id=?", cid)
	errors.PanicAndRollbackOnErr(err, tx)
	defer rows.Close()
	for rows.Next() {
		members = append(members, parseString(tx, rows))
	}
	return members
}

func toTypeString(ctype int) string {
	switch ctype {
	case CHANNEL:
		return "CHANNEL"
	case DIRECT:
		return "DIRECT"
	default:
		panic("unknown convo type: " + string(ctype))
	}
}

// get the values of a map as a slice
// no generics because NO GENERICS!
func values(m map[string]Convo) []Convo {
	slice := make([]Convo, 0, len(m))
	for _, val := range m {
		slice = append(slice, val)
	}
	return slice
}

func hexDecode(tx *sql.Tx, s string) []byte {
	if s == "" {
		return nil
	}
	sid, err := hex.DecodeString(s)
	errors.PanicAndRollbackOnErr(err, tx)
	return sid
}
