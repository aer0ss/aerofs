package main

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/errors"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"fmt"
	"github.com/emicklei/go-restful"
	"time"
)

type stringSet map[string]struct{}

type GroupsResource struct {
	broadcaster broadcast.Broadcaster
	db          *sql.DB
}

//
// Route definitions
//

func BuildGroupsRoutes(db *sql.DB, broadcaster broadcast.Broadcaster) *restful.WebService {
	g := GroupsResource{
		broadcaster: broadcaster,
		db:          db,
	}
	ws := new(restful.WebService)
	ws.Filter(CheckUser)
	ws.Filter(UpdateLastOnline)
	ws.Filter(LogRequest)

	ws.Path("/groups").
		Doc("Manage group membership, settings, and messages").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON)

	//
	// path: /groups
	//

	ws.Route(ws.GET("").To(g.getAll).
		Doc("Get a list of all joined and public groups").
		Returns(200, "A list of groups", GroupList{}).
		Returns(401, "Invalid Authorization", nil))

	ws.Route(ws.POST("").To(g.createGroup).
		Doc("Create a new group").
		Reads(GroupWritable{}).
		Returns(200, "The newly-created group", Group{}).
		Returns(401, "Invalid Authorization", nil))

	//
	// path: /groups/{gid}
	//

	ws.Route(ws.GET("/{gid}").To(g.getById).
		Doc("Get group info").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "Group info", Group{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(404, "Group does not exist", nil))

	ws.Route(ws.PUT("/{gid}").To(g.updateGroup).
		Doc("Edit group info").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Reads(GroupWritable{}).
		Returns(200, "Group info", Group{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group does not exist", nil)) // N.B. use PATCH?

	//
	// path: /groups/{gid}/members
	//

	ws.Route(ws.GET("/{gid}/members").To(g.getMembers).
		Doc("Get group members' ids").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "Group member ids", IdList{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group does not exist", nil))

	//
	// path: /groups/{gid}/members/{uid}
	//

	ws.Route(ws.PUT("/{gid}/members/{uid}").To(g.addMember).
		Doc("Add user to group").
		Notes("Returns 200 if user was already a member (noop)").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Successfully added", nil). // N.B. return group membership list?
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group or user not found", nil))

	ws.Route(ws.DELETE("/{gid}/members/{uid}").To(g.removeMember).
		Doc("Remove user from group").
		Notes("Returns 200 if user was already not a member (noop)").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Successfully removed", nil). // N.B. return group membership list?
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group not found", nil))

	//
	// path: /groups/{gid}/member_history
	//

	ws.Route(ws.GET("/{gid}/member_history").To(g.getMemberHistory).
		Doc("Get group membership history").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "Group membership history", nil).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group or user not found", nil))

	//
	// path: /groups/{gid}/messages
	//

	ws.Route(ws.GET("/{gid}/messages").To(g.getMessages).
		Doc("Get all messages exchanged in a group").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "A list of messages exchanged", MessageList{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group not found", nil))

	ws.Route(ws.POST("/{gid}/messages").To(g.newMessage).
		Doc("Send a new message to a group").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Reads("string").
		Returns(200, "The newly-created message", Message{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group not found", nil))

	//
	// path: /groups/{gid}/receipts
	//

	ws.Route(ws.GET("/{gid}/receipts").To(g.getReceipts).
		Doc("Get last read receipt for all members of the conversation").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "Last-read receipts for all group members", LastReadReceiptList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group not found", nil))

	ws.Route(ws.POST("/{gid}/receipts").To(g.updateReceipt).
		Doc("Set the last read message id for the group conversation").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Reads(LastReadReceiptWritable{}).
		Returns(200, "The created last-read receipt", LastReadReceipt{}).
		Returns(400, "Missing required key", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Given message id not found in conversation", nil))

	return ws
}

//
// Handlers
//

func (g GroupsResource) getAll(request *restful.Request, response *restful.Response) {
	owner := request.Attribute(AuthorizedUser).(string)
	groups := make(map[string]Group)
	// query db
	rows, err := g.db.Query(fmt.Sprint(
		"SELECT id,created_time,name,is_public,user_id ",
		"FROM groups ",
		"LEFT JOIN group_members ON id=group_id ",
		"WHERE id IN (SELECT group_id from group_members WHERE user_id=?) ",
		"OR id IN (SELECT id from groups WHERE is_public=1) ",
	), owner)
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
			group.addMember(uid.String)
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
				group.addMember(uid.String)
			}
		}
		// necessary because go doesn't allow assigning to map value
		// https://code.google.com/p/go/issues/detail?id=3117
		groups[gid] = group
	}
	// write response
	response.WriteEntity(GroupList{Groups: values(groups)})
}

func (g GroupsResource) createGroup(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(AuthorizedUser).(string)
	// read body
	params := new(GroupWritable)
	err := request.ReadEntity(params)
	errors.PanicOnErr(err)
	// check body
	if params.Name == nil || params.Members == nil || params.IsPublic == nil {
		response.WriteErrorString(400, "Request body must have \"name\", \"is_public\", and \"members\" keys")
		return
	}
	// generate gid, time
	gid, err := generateRandomId()
	errors.PanicOnErr(err)
	createdTime := time.Now()
	// begin transaction
	tx := BeginOrPanic(g.db)
	// insert into db
	_, err = tx.Exec("INSERT INTO groups (id,created_time,name,is_public) VALUES (?,?,?,?)", gid, createdTime.UnixNano(), params.Name, params.IsPublic)
	errors.PanicAndRollbackOnErr(err, tx)
	for _, uid := range params.Members {
		_, err = tx.Exec("INSERT INTO group_members (user_id,group_id) VALUES (?,?)", uid, gid)
		errors.PanicAndRollbackOnErr(err, tx)
		err = insertGroupMemberChange(tx, gid, uid, caller, createdTime, true)
		errors.PanicAndRollbackOnErr(err, tx)
	}
	// end transaction
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(Group{
		Id:          gid,
		CreatedTime: createdTime,
		Name:        *params.Name,
		IsPublic:    *params.IsPublic,
		Members:     params.Members,
	})
	// send event
	sendGroupEvent(g.broadcaster, gid, params.Members)
}

func (g GroupsResource) getById(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	// start transaction
	tx := BeginOrPanic(g.db)
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// end transaction
	CommitOrPanic(tx)
	// check that caller is in group or group is public
	caller := request.Attribute(AuthorizedUser).(string)
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}
	// write response
	response.WriteEntity(group)
}

func (g GroupsResource) updateGroup(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(AuthorizedUser).(string)
	// read the request body
	params := new(GroupWritable)
	err := request.ReadEntity(params)
	errors.PanicOnErr(err)
	// start transaction
	tx := BeginOrPanic(g.db)
	// query db for group info
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// update db
	group, err = updateGroup(tx, group, params, caller)
	errors.PanicAndRollbackOnErr(err, tx)
	// end transaction
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(group)
	// send event
	var targets []string
	if !group.IsPublic {
		targets = group.Members
	}
	sendGroupEvent(g.broadcaster, gid, targets)
}

func (g GroupsResource) getMembers(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	// start transaction
	tx := BeginOrPanic(g.db)
	// query db
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// end transaction
	CommitOrPanic(tx)
	// check that caller is in group or group is public
	caller := request.Attribute(AuthorizedUser).(string)
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}
	// write response
	response.WriteEntity(IdList{
		Ids: group.Members,
	})
}

func (g GroupsResource) addMember(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	uid := request.PathParameter("uid")
	caller := request.Attribute(AuthorizedUser).(string)
	// start transaction
	tx := BeginOrPanic(g.db)
	// query db for group info
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// update db
	_, err = tx.Exec("INSERT INTO group_members (group_id,user_id) VALUES (?,?)", gid, uid)
	// rely on MySQL constraints for error checks
	if errors.ForeignKeyConstraintFailed(err) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	} else if errors.UniqueConstraintFailed(err) {
		// user is already a member of the group, that's ok!
		tx.Rollback()
		return
	}
	errors.PanicAndRollbackOnErr(err, tx)
	// edit member history
	err = insertGroupMemberChange(tx, gid, uid, caller, time.Now(), true)
	errors.PanicAndRollbackOnErr(err, tx)
	// end transaction
	CommitOrPanic(tx)
	// send event
	var targets []string
	if !group.IsPublic {
		group.addMember(uid) // ensure newly-added uid is in the list
		targets = group.Members
	}
	sendGroupEvent(g.broadcaster, gid, targets)
}

func (g GroupsResource) removeMember(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	uid := request.PathParameter("uid")
	caller := request.Attribute(AuthorizedUser).(string)
	// start transaction
	tx := BeginOrPanic(g.db)
	// query db for group info
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// remove member
	result, err := tx.Exec("DELETE FROM group_members WHERE group_id=? AND user_id=?", gid, uid)
	errors.PanicAndRollbackOnErr(err, tx)
	// exit early if uid was already not a member
	rowsAffected, err := result.RowsAffected()
	errors.PanicAndRollbackOnErr(err, tx)
	if rowsAffected == 0 {
		tx.Rollback()
		return
	}
	// edit membership history
	err = insertGroupMemberChange(tx, gid, uid, caller, time.Now(), false)
	errors.PanicAndRollbackOnErr(err, tx)
	// end transaction
	CommitOrPanic(tx)
	// send event
	var targets []string
	if !group.IsPublic {
		targets = group.Members
	}
	sendGroupEvent(g.broadcaster, gid, targets)
}

func (g GroupsResource) getMemberHistory(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(AuthorizedUser).(string)
	tx := BeginOrPanic(g.db)
	group, err := getGroup(tx, gid)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	history, err := getGroupMemberHistory(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	CommitOrPanic(tx)
	response.WriteEntity(GroupMemberHistory{History: history})
}

func (g GroupsResource) getMessages(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(AuthorizedUser).(string)
	// start transaction
	tx := BeginOrPanic(g.db)
	// query db for group info
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// query db for messages
	messages := make([]Message, 0)
	rows, err := tx.Query("SELECT id,time,body,from_id,to_id FROM messages WHERE to_id=? ORDER BY time", gid)
	errors.PanicAndRollbackOnErr(err, tx)
	defer rows.Close()
	// parse each message
	for rows.Next() {
		var msg Message
		var timeUnixNanos int64
		err := rows.Scan(&msg.Id, &timeUnixNanos, &msg.Body, &msg.From, &msg.To)
		errors.PanicAndRollbackOnErr(err, tx)
		msg.Time = time.Unix(0, timeUnixNanos)
		messages = append(messages, msg)
	}
	// end transaction
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(MessageList{Messages: messages})
}

func (g GroupsResource) newMessage(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(AuthorizedUser).(string)
	// read body
	params := new(MessageWritable)
	err := request.ReadEntity(params)
	if params.Body == "" {
		response.WriteErrorString(400, "Missing \"body\" key")
		return
	}
	// start transaction
	tx := BeginOrPanic(g.db)
	// query db for group info
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group
	if !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// insert message into db
	message := Message{
		Time: time.Now(),
		Body: params.Body,
		From: caller,
		To:   gid,
	}
	res, err := tx.Exec("INSERT INTO messages (time,body,from_id,to_id) VALUES (?,?,?,?)",
		message.Time.UnixNano(),
		message.Body,
		message.From,
		message.To)
	errors.PanicAndRollbackOnErr(err, tx)
	// get message id
	message.Id, err = res.LastInsertId()
	errors.PanicAndRollbackOnErr(err, tx)
	// end transaction
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(message)
	// send event
	sendGroupMessageEvent(g.broadcaster, gid, group.Members)
}

func (g GroupsResource) getReceipts(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(AuthorizedUser).(string)
	// start transaction
	tx := BeginOrPanic(g.db)
	// query db for group info
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// query db for receipts
	receipts := make([]LastReadReceipt, 0)
	rows, err := tx.Query("SELECT user_id,msg_id,time FROM last_read WHERE convo_id=?", gid)
	errors.PanicAndRollbackOnErr(err, tx)
	defer rows.Close()
	// parse each receipt
	for rows.Next() {
		var receipt LastReadReceipt
		var timeUnixNanos int64
		err := rows.Scan(&receipt.UserId, &receipt.MessageId, &timeUnixNanos)
		errors.PanicAndRollbackOnErr(err, tx)
		receipt.Time = time.Unix(0, timeUnixNanos)
		receipts = append(receipts, receipt)
	}
	CommitOrPanic(tx)
	// compose response
	response.WriteEntity(LastReadReceiptList{LastRead: receipts})
}

func (g GroupsResource) updateReceipt(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(AuthorizedUser).(string)
	// read request body
	params := new(LastReadReceiptWritable)
	err := request.ReadEntity(params)
	if params.MessageId == 0 {
		response.WriteErrorString(400, "Request body must have \"messageId\" key")
		return
	}
	// start transaction
	tx := BeginOrPanic(g.db)
	// query db for group info
	group, err := getGroup(tx, gid)
	errors.PanicAndRollbackOnErr(err, tx)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.hasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// check that the message exists in the conversation
	err = tx.QueryRow("SELECT 1 FROM messages WHERE id=? AND to_id=?",
		params.MessageId,
		gid,
	).Scan(new(int))
	if err == sql.ErrNoRows {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// update db
	receipt := LastReadReceipt{
		UserId:    caller,
		MessageId: params.MessageId,
		Time:      time.Now(),
	}
	_, err = tx.Exec("INSERT INTO last_read (user_id,convo_id,msg_id,time) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE msg_id=?, time=?",
		receipt.UserId,
		gid,
		receipt.MessageId,
		receipt.Time.UnixNano(),
		receipt.MessageId,
		receipt.Time.UnixNano(),
	)
	errors.PanicAndRollbackOnErr(err, tx)
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(receipt)
	sendGroupMessageReadEvent(g.broadcaster, gid, group.Members)
}

//
// Helpers
//

// returns hex-encoded 128-bits
func generateRandomId() (string, error) {
	b := make([]byte, 16)
	_, err := rand.Read(b)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
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

// helps to avoid the ugly append syntax
func (g *Group) addMember(uid string) {
	if g.Members == nil {
		g.Members = make([]string, 0)
	}
	// ensure idempotency
	for _, val := range g.Members {
		if val == uid {
			return
		}
	}
	g.Members = append(g.Members, uid)
}

// helper for array contains
func (g *Group) hasMember(uid string) bool {
	for _, val := range g.Members {
		if val == uid {
			return true
		}
	}
	return false
}

func sliceToSet(s []string) stringSet {
	m := make(stringSet)
	for _, v := range s {
		m[v] = struct{}{}
	}
	return m
}

func difference(a, b []string) stringSet {
	s := sliceToSet(a)
	for _, v := range b {
		delete(s, v)
	}
	return s
}

//
// Db Helpers
//

// Returns a full Group object, or nil if none found with given id
func getGroup(tx *sql.Tx, gid string) (*Group, error) {
	var group Group
	var unixTimeNanos int64
	// get group info
	err := tx.QueryRow("SELECT created_time,name,is_public FROM groups WHERE id=?", gid).
		Scan(&unixTimeNanos, &group.Name, &group.IsPublic)
	if err == sql.ErrNoRows {
		return nil, nil
	} else if err != nil {
		return nil, err
	}
	// get group members
	rows, err := tx.Query("SELECT user_id FROM group_members WHERE group_id=?", gid)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	members := make([]string, 0)
	for rows.Next() {
		var uid string
		err := rows.Scan(&uid)
		if err != nil {
			return nil, err
		}
		members = append(members, uid)
	}
	// build group object
	group.Id = gid
	group.CreatedTime = time.Unix(0, unixTimeNanos)
	group.Members = members
	return &group, nil
}

// Patches the given group with whatever params are present in the
// GroupWritable, and updates the db.
// Returns the modified group
// N.B. does not check for group's existence!
func updateGroup(tx *sql.Tx, group *Group, params *GroupWritable, caller string) (*Group, error) {
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
	if err != nil {
		return nil, err
	}
	// exit early if no changes made to membership
	if params.Members == nil || (len(membersAdded) == 0 && len(membersRemoved) == 0) {
		return group, nil
	}
	// update group_members and group_member_history tables
	updateTime := time.Now()
	for uid := range membersRemoved {
		_, err = tx.Exec("DELETE FROM group_members WHERE group_id=? AND user_id=?", group.Id, uid)
		if err != nil {
			return nil, err
		}
		err = insertGroupMemberChange(tx, group.Id, uid, caller, updateTime, false)
		if err != nil {
			return nil, err
		}
	}
	for uid := range membersAdded {
		_, err = tx.Exec("INSERT INTO group_members (group_id, user_id) VALUES (?,?)", group.Id, uid)
		if err != nil {
			return nil, err
		}
		err = insertGroupMemberChange(tx, group.Id, uid, caller, updateTime, true)
		if err != nil {
			return nil, err
		}
	}
	// return updated group
	return group, nil
}

func getGroupMemberHistory(tx *sql.Tx, gid string) ([]GroupMemberChange, error) {
	rows, err := tx.Query("SELECT user_id,caller_id,time,added FROM group_member_history WHERE group_id=? ORDER BY time", gid)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	changes := make([]GroupMemberChange, 0)
	for rows.Next() {
		c, err := parseGroupMemberHistoryRow(rows)
		if err != nil {
			return nil, err
		}
		changes = append(changes, *c)
	}
	return changes, nil
}

func parseGroupMemberHistoryRow(row Scannable) (*GroupMemberChange, error) {
	var c GroupMemberChange
	var unixTimeNanos int64
	err := row.Scan(&c.UserId, &c.CallerId, &unixTimeNanos, &c.Added)
	if err != nil {
		return nil, err
	}
	c.Time = time.Unix(0, unixTimeNanos)
	return &c, nil
}

func insertGroupMemberChange(tx *sql.Tx, gid, uid, caller string, time time.Time, added bool) error {
	_, err := tx.Exec("INSERT INTO group_member_history (group_id,user_id,caller_id,time,added) VALUES (?,?,?,?,?)",
		gid, uid, caller, time.UnixNano(), added,
	)
	return err
}
