package main

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/errors"
	"database/sql"
	"fmt"
	"github.com/emicklei/go-restful"
	"io/ioutil"
	"log"
	"math/rand"
	"strings"
	"time"
)

const MAX_AVATAR_SIZE = 64 * 1024 // 64KB

type UsersResource struct {
	broadcaster broadcast.Broadcaster
	db          *sql.DB
}

//
// Route definitions
//

func BuildUsersRoutes(db *sql.DB, broadcaster broadcast.Broadcaster) *restful.WebService {
	u := UsersResource{
		broadcaster: broadcaster,
		db:          db,
	}
	ws := new(restful.WebService)
	ws.Filter(CheckUser)
	ws.Filter(UpdateLastOnline)
	ws.Filter(LogRequest)

	ws.Path("/users").
		Doc("Manage user profiles and messages").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON)

	//
	// path: /users
	//

	ws.Route(ws.GET("").To(u.getAll).
		Doc("Get all user's profiles").
		Returns(200, "All users", UserList{}).
		Returns(401, "Invalid authorization", nil))

	//
	// path: /users/{uid}
	//

	ws.Route(ws.GET("/{uid}").To(u.getById).
		Doc("Get user's profile").
		Notes("Note the absence of the avatar. It must be requested separately due to its size.").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "User info", User{}).
		Returns(401, "Invalid authorization", nil).
		Returns(404, "User does not exist", nil))

	ws.Route(ws.PUT("/{uid}").Filter(UserIsTarget).To(u.updateUser).
		Doc("Update user's profile info").
		Notes("First and last name must always be provided, even if unchanged.").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Reads(UserWritable{}).
		Returns(200, "User updated", User{}).
		Returns(400, "Missing required key", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(409, "Conflict", nil))

	//
	// path: /users/{uid}/avatar
	//

	ws.Route(ws.GET("/{uid}/avatar").To(u.getAvatar).
		Doc("Get user's avatar").
		Notes("This returns the raw avatar data. It should be hot-linkable in an img tag.\nAn authorization token can alternatively be provided in the Cookie header, in the form \"Cookie: authorization=<token>\"").
		Produces(restful.MIME_OCTET).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Avatar updated", "bytestring").
		Returns(401, "Invalid authorization", nil).
		Returns(404, "No avatar found", nil))

	ws.Route(ws.PUT("/{uid}/avatar").Filter(UserIsTarget).To(u.updateAvatar).
		Doc("Get user's avatar").
		Notes("This expects the raw avatar data in the body.").
		Consumes(restful.MIME_OCTET).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Reads("bytestring").
		Returns(200, "User updated", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(413, "Request entity too large", nil))

	//
	// path: /users/{uid}/pinned
	//

	ws.Route(ws.GET("/{uid}/pinned").Filter(UserIsTarget).To(u.getPinned).
		Doc("Get the list of pinned conversations").
		Notes("This returns a list of group and user ids that have been pinned").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "List of pinned ids", IdList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil))

	//
	// path: /users/{uid}/pinned/{cid}
	//

	ws.Route(ws.PUT("/{uid}/pinned/{cid}").Filter(UserIsTarget).To(u.pinConvo).
		Doc("Pin a conversation").
		Notes("User must be a member of any group they wish to pin").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Param(ws.PathParameter("cid", "User id or group id of conversation to pin").DataType("string")).
		Returns(200, "Conversation pinned", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Not Found", nil))

	ws.Route(ws.DELETE("/{uid}/pinned/{cid}").Filter(UserIsTarget).To(u.unpinConvo).
		Doc("Unpin a conversation").
		Notes("This request is idempotent and may return 200 even if the conversation was never originally pinned").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Param(ws.PathParameter("cid", "User id or group id of conversation to unpin").DataType("string")).
		Returns(200, "Conversation not pinned", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil))

	//
	// path: /users/{uid}/messages
	//

	ws.Route(ws.GET("/{uid}/messages").To(u.getMessages).
		Doc("Get all messages exchanged with a user").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "A list of messages exchanged", MessageList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(404, "User not found", nil))

	ws.Route(ws.POST("/{uid}/messages").To(u.newMessage).
		Doc("Send a new message to a user").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Reads(MessageWritable{}).
		Returns(200, "The newly-created message", Message{}).
		Returns(400, "Missing required key", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(404, "User not found", nil))

	//
	// path: /users/{uid}/receipts
	//

	ws.Route(ws.GET("/{uid}/receipts").To(u.getReceipts).
		Doc("Get last read receipt for both members of the conversation").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Last-read receipts for both users", LastReadReceiptList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(404, "User not found", nil))

	ws.Route(ws.POST("/{uid}/receipts").To(u.updateReceipt).
		Doc("Set the last read message id for the 1v1 conversation").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Reads(LastReadReceiptWritable{}).
		Returns(200, "The created last-read receipt", LastReadReceipt{}).
		Returns(400, "Missing required key", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(404, "Given message id not found in conversation", nil))

	return ws
}

//
// Handlers
//

func (u UsersResource) getAll(request *restful.Request, response *restful.Response) {
	userList := make([]User, 0)
	rows, err := u.db.Query("SELECT id,first_name,last_name,tag_id FROM users")
	errors.PanicOnErr(err)
	defer rows.Close()
	now := time.Now()
	for rows.Next() {
		var user User
		var tagId sql.NullString
		err := rows.Scan(&user.Id, &user.FirstName, &user.LastName, &tagId)
		errors.PanicOnErr(err)
		if val, err := tagId.Value(); err != nil {
			errors.PanicOnErr(err)
		} else if val != nil {
			user.TagId = val.(string)
		}
		user.LastOnlineTime = getLastOnlineTime(user.Id, now)
		userList = append(userList, user)
	}
	response.WriteEntity(UserList{Users: userList})
}

func (u UsersResource) getById(request *restful.Request, response *restful.Response) {
	id := request.PathParameter("uid")
	// begin transaction
	tx := BeginOrPanic(u.db)
	// query db
	user, err := getUser(tx, id)
	errors.PanicAndRollbackOnErr(err, tx)
	// end transaction
	CommitOrPanic(tx)
	// write response
	if user == nil {
		response.WriteHeader(404)
	} else {
		response.WriteEntity(user)
	}
}

func (u UsersResource) updateUser(request *restful.Request, response *restful.Response) {
	id := request.PathParameter("uid")
	// read params
	params := new(UserWritable)
	err := request.ReadEntity(params)
	if err != nil || params.FirstName == "" || params.LastName == "" {
		response.WriteErrorString(400, "Request body must have \"firstName\" and \"lastName\" keys")
		return
	}
	// generate tag id if necessary
	var tagId string
	if params.TagId == "" {
		tagId = strings.ToLower(params.FirstName + params.LastName)
	} else {
		tagId = params.TagId
	}
	// start transaction
	tx := BeginOrPanic(u.db)
	// determine whether user exists already
	oldUser, err := getUser(tx, id)
	errors.PanicAndRollbackOnErr(err, tx)
	var dbFunc func(*sql.Tx, *User) error
	if oldUser == nil {
		dbFunc = insertUser
	} else {
		dbFunc = updateUser
	}
	// update db
	newUser := User{
		Id:        id,
		FirstName: params.FirstName,
		LastName:  params.LastName,
		TagId:     tagId,
	}
	err = dbFunc(tx, &newUser)
	if errors.UniqueConstraintFailed(err) {
		if newUser.TagId == params.TagId {
			// tag collision with given tag id
			response.WriteErrorString(409, "User with tagId "+tagId+" already exists")
			tx.Rollback()
			return
		} else {
			// tag collision with default tag id; append a random 32-bit number
			newUser.TagId = newUser.TagId + fmt.Sprintf("%v", rand.Uint32())
			err = dbFunc(tx, &newUser)
			errors.PanicAndRollbackOnErr(err, tx)
		}
	} else {
		errors.PanicAndRollbackOnErr(err, tx)
	}
	// end transaction
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(newUser)
	// broadcast event
	broadcastUserEvent(u.broadcaster, id)
}

func (u UsersResource) getMessages(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	caller := request.Attribute(AuthorizedUser).(string)
	messages := make([]Message, 0)
	tx := BeginOrPanic(u.db)
	// ensure user exists
	if !userExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// query db for messages
	rows, err := tx.Query("SELECT id,time,body,from_id,to_id FROM messages WHERE (from_id=? AND to_id=?) OR (from_id=? AND to_id=?) ORDER BY time", uid, caller, caller, uid)
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
	CommitOrPanic(tx)
	// compose response
	response.WriteEntity(MessageList{Messages: messages})
}

func (u UsersResource) newMessage(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	caller := request.Attribute(AuthorizedUser).(string)
	params := new(MessageWritable)
	err := request.ReadEntity(params)
	// check for body
	if params.Body == "" {
		response.WriteErrorString(400, "Missing \"body\" key")
		return
	}
	// begin transaction
	tx := BeginOrPanic(u.db)
	// ensure user exists
	if !userExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// insert message into db
	message := Message{
		Time: time.Now(),
		Body: params.Body,
		From: caller,
		To:   uid,
	}
	res, err := tx.Exec("INSERT INTO messages (time,body,from_id,to_id) VALUES (?,?,?,?)",
		message.Time.UnixNano(),
		message.Body,
		message.From,
		message.To)
	errors.PanicAndRollbackOnErr(err, tx)
	// end transaction
	CommitOrPanic(tx)
	// write response
	message.Id, err = res.LastInsertId()
	errors.PanicOnErr(err)
	response.WriteEntity(message)
	// broadcast event
	broadcastUserMessageEvent(u.broadcaster, caller)
}

func (u UsersResource) getAvatar(request *restful.Request, response *restful.Response) {
	id := request.PathParameter("uid")
	// query db
	var avatar []byte
	err := u.db.QueryRow("SELECT avatar FROM users WHERE id=?", id).Scan(&avatar)
	switch {
	case err == sql.ErrNoRows || len(avatar) == 0:
		response.WriteHeader(404)
	case err != nil:
		errors.PanicOnErr(err)
	default:
		response.Write(avatar)
	}
}

func (u UsersResource) updateAvatar(request *restful.Request, response *restful.Response) {
	id := request.PathParameter("uid")
	// limit avatar size
	if request.Request.ContentLength > MAX_AVATAR_SIZE {
		response.WriteErrorString(400, "blah.")
		// response.WriteErrorString(413, fmt.Sprint("Avatar size cannot exceed ", MAX_AVATAR_SIZE, " bytes"))
		return
	}
	// read body
	bytes, err := ioutil.ReadAll(request.Request.Body)
	errors.PanicOnErr(err)
	// update db
	_, err = u.db.Exec("UPDATE users SET avatar=? WHERE id=?", bytes, id)
	errors.PanicOnErr(err)
	// broadcast event
	broadcastUserAvatarEvent(u.broadcaster, id)
}

func (u UsersResource) getReceipts(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	caller := request.Attribute(AuthorizedUser).(string)
	receipts := make([]LastReadReceipt, 0)
	// start transaction
	tx := BeginOrPanic(u.db)
	// ensure user exists
	if !userExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// query db for matching receipts
	rows, err := tx.Query("SELECT user_id,msg_id,time FROM last_read WHERE (user_id=? AND convo_id=?) OR (user_id=? AND convo_id=?)", uid, caller, caller, uid)
	errors.PanicOnErr(err)
	defer rows.Close()
	// parse each receipt
	for rows.Next() {
		var receipt LastReadReceipt
		var timeUnixNanos int64
		err := rows.Scan(&receipt.UserId, &receipt.MessageId, &timeUnixNanos)
		errors.PanicOnErr(err)
		receipt.Time = time.Unix(0, timeUnixNanos)
		receipts = append(receipts, receipt)
	}
	CommitOrPanic(tx)
	// compose response
	response.WriteEntity(LastReadReceiptList{LastRead: receipts})
}

func (u UsersResource) updateReceipt(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	caller := request.Attribute(AuthorizedUser).(string)
	// read request body
	params := new(LastReadReceiptWritable)
	err := request.ReadEntity(params)
	if params.MessageId == 0 {
		response.WriteErrorString(400, "Request body must have \"messageId\" key")
		return
	}
	// check that the message exists in the conversation
	tx := BeginOrPanic(u.db)
	err = tx.QueryRow("SELECT 1 FROM messages WHERE id=? AND ((from_id=? AND to_id=?) OR (from_id=? AND to_id=?))",
		params.MessageId,
		caller,
		uid,
		uid,
		caller,
	).Scan(new(int))
	if err == sql.ErrNoRows {
		response.WriteHeader(404)
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
		uid,
		receipt.MessageId,
		receipt.Time.UnixNano(),
		receipt.MessageId,
		receipt.Time.UnixNano(),
	)
	errors.PanicOnErr(err)
	// end transaction
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(receipt)
	broadcastUserMessageReadEvent(u.broadcaster, caller)
}

func (u UsersResource) getPinned(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(AuthorizedUser).(string)
	cids := make([]string, 0)
	// query db
	rows, err := u.db.Query("SELECT convo_id FROM pinned WHERE user_id=?", caller)
	errors.PanicOnErr(err)
	defer rows.Close()
	// parse each row
	for rows.Next() {
		var cid string
		err := rows.Scan(&cid)
		errors.PanicOnErr(err)
		cids = append(cids, cid)
	}
	// return id list
	response.WriteEntity(IdList{Ids: cids})
}

func (u UsersResource) pinConvo(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(AuthorizedUser).(string)
	// start transaction
	tx := BeginOrPanic(u.db)
	// ensure convo exists
	err := tx.QueryRow("SELECT 1 FROM users WHERE id=?", cid).Scan(new(int))
	if err == sql.ErrNoRows {
		err = tx.QueryRow("SELECT 1 from groups where id=?", cid).Scan(new(int))
		if err == sql.ErrNoRows {
			tx.Rollback()
			response.WriteHeader(404)
			return
		}
	}
	errors.PanicOnErr(err)
	// insert row into db
	_, err = tx.Exec("INSERT INTO pinned (user_id,convo_id) VALUES (?,?)", caller, cid)
	if errors.UniqueConstraintFailed(err) {
		errors.PanicOnErr(err)
	}
	// end transaction
	CommitOrPanic(tx)
}

func (u UsersResource) unpinConvo(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(AuthorizedUser).(string)
	// delete row
	_, err := u.db.Exec("DELETE FROM pinned WHERE user_id=? AND convo_id=?", caller, cid)
	errors.PanicOnErr(err)
}

//
// DB Helpers
//

func userExists(tx *sql.Tx, uid string) bool {
	err := tx.QueryRow("SELECT 1 FROM users WHERE id=?", uid).Scan(new(int))
	if err == sql.ErrNoRows {
		return false
	}
	errors.PanicOnErr(err)
	return true
}

func getUser(tx *sql.Tx, uid string) (*User, error) {
	var user User
	var tagId sql.NullString
	err := tx.QueryRow("SELECT id,first_name,last_name,tag_id FROM users WHERE id=?", uid).
		Scan(&user.Id, &user.FirstName, &user.LastName, &tagId)
	if err == sql.ErrNoRows {
		return nil, nil
	} else if err != nil {
		return nil, err
	}
	if val, err := tagId.Value(); err != nil {
		errors.PanicOnErr(err)
	} else if val != nil {
		user.TagId = val.(string)
	}
	user.LastOnlineTime = getLastOnlineTime(user.Id, time.Now())
	return &user, nil
}

func insertUser(tx *sql.Tx, user *User) error {
	_, err := tx.Exec("INSERT INTO users (id, first_name, last_name, tag_id) VALUES (?,?,?,?)",
		user.Id, user.FirstName, user.LastName, user.TagId,
	)
	return err
}

func updateUser(tx *sql.Tx, user *User) error {
	log.Print("UPDATE users SET id=?, first_name=?, last_name=?, tag_id=? WHERE id=? ",
		user.Id, user.FirstName, user.LastName, user.TagId, user.Id)
	_, err := tx.Exec("UPDATE users SET id=?, first_name=?, last_name=?, tag_id=? WHERE id=?",
		user.Id, user.FirstName, user.LastName, user.TagId, user.Id,
	)
	return err
}

//
// Misc Helpers
//

// Return the number of seconds since the user was last seen online, or nil if
// the user has never made a request.
func getLastOnlineTime(uid string, now time.Time) *uint64 {
	lastOnlineTimesMutex.RLock()
	t, ok := lastOnlineTimes[uid]
	lastOnlineTimesMutex.RUnlock()
	if ok {
		since := uint64(now.Sub(t).Seconds())
		return &since
	} else {
		return nil
	}
}
