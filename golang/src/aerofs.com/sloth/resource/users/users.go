package users

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/lastOnline"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"fmt"
	"github.com/emicklei/go-restful"
	"io/ioutil"
	"math/rand"
	"strings"
	"time"
)

const MAX_AVATAR_SIZE = 64 * 1024 // 64KB

type context struct {
	broadcaster     broadcast.Broadcaster
	db              *sql.DB
	lastOnlineTimes *lastOnline.Times
}

//
// Route definitions
//

func BuildRoutes(
	db *sql.DB,
	broadcaster broadcast.Broadcaster,
	lastOnlineTimes *lastOnline.Times,
	checkUser restful.FilterFunction,
	updateLastOnline restful.FilterFunction,

) *restful.WebService {
	ctx := &context{
		broadcaster:     broadcaster,
		db:              db,
		lastOnlineTimes: lastOnlineTimes,
	}
	ws := new(restful.WebService)
	ws.Filter(checkUser)
	ws.Filter(updateLastOnline)
	ws.Filter(filters.LogRequest)

	ws.Path("/users").
		Doc("Manage user profiles and messages").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON)

	//
	// path: /users
	//

	ws.Route(ws.GET("").To(ctx.getAll).
		Doc("Get all user's profiles").
		Returns(200, "All users", UserList{}).
		Returns(401, "Invalid authorization", nil))

	//
	// path: /users/{uid}
	//

	ws.Route(ws.GET("/{uid}").To(ctx.getById).
		Doc("Get user's profile").
		Notes("Note that the avatarPath is provided if an avatar has been uploaded. The avatar data must be requested separately at the provided path.").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "User info", User{}).
		Returns(401, "Invalid authorization", nil).
		Returns(404, "User does not exist", nil))

	ws.Route(ws.PUT("/{uid}").Filter(filters.UserIsTarget).To(ctx.updateUser).
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

	ws.Route(ws.GET("/{uid}/avatar").To(ctx.getAvatar).
		Doc("Get user's avatar").
		Notes("This returns the raw avatar data. It should be hot-linkable in an img tag.\nAn authorization token can alternatively be provided in the Cookie header, in the form \"Cookie: authorization=<token>\"").
		Produces(restful.MIME_OCTET).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Avatar updated", "bytestring").
		Returns(401, "Invalid authorization", nil).
		Returns(404, "No avatar found", nil))

	ws.Route(ws.PUT("/{uid}/avatar").Filter(filters.UserIsTarget).To(ctx.updateAvatar).
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

	ws.Route(ws.GET("/{uid}/pinned").Filter(filters.UserIsTarget).To(ctx.getPinned).
		Doc("Get the list of pinned conversations").
		Notes("This returns a list of group and user ids that have been pinned").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "List of pinned ids", IdList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil))

	//
	// path: /users/{uid}/pinned/{cid}
	//

	ws.Route(ws.PUT("/{uid}/pinned/{cid}").Filter(filters.UserIsTarget).To(ctx.pinConvo).
		Doc("Pin a conversation").
		Notes("User must be a member of any group they wish to pin").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Param(ws.PathParameter("cid", "User id or group id of conversation to pin").DataType("string")).
		Returns(200, "Conversation pinned", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Not Found", nil))

	ws.Route(ws.DELETE("/{uid}/pinned/{cid}").Filter(filters.UserIsTarget).To(ctx.unpinConvo).
		Doc("Unpin a conversation").
		Notes("This request is idempotent and may return 200 even if the conversation was never originally pinned").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Param(ws.PathParameter("cid", "User id or group id of conversation to unpin").DataType("string")).
		Returns(200, "Conversation not pinned", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil))

	//
	// path: /users/{uid}/typing/{cid}
	//

	ws.Route(ws.POST("/{uid}/typing/{cid}").Filter(filters.UserIsTarget).To(ctx.postTyping).
		Doc("Mark the user as \"typing\" in a conversation").
		Notes("User must be a member of any group in which they are typing").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Param(ws.PathParameter("cid", "User id or group id of conversation").DataType("string")).
		Returns(200, "Marked as \"typing\"", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Not Found", nil))

	//
	// path: /users/{uid}/messages
	//

	ws.Route(ws.GET("/{uid}/messages").To(ctx.getMessages).
		Doc("Get all messages exchanged with a user").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "A list of messages exchanged", MessageList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(404, "User not found", nil))

	ws.Route(ws.POST("/{uid}/messages").To(ctx.newMessage).
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

	ws.Route(ws.GET("/{uid}/receipts").To(ctx.getReceipts).
		Doc("Get last read receipt for both members of the conversation").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Last-read receipts for both users", LastReadReceiptList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(404, "User not found", nil))

	ws.Route(ws.POST("/{uid}/receipts").To(ctx.updateReceipt).
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

func (ctx *context) getAll(request *restful.Request, response *restful.Response) {
	tx := dao.BeginOrPanic(ctx.db)
	userList := dao.GetAllUsers(tx, ctx.lastOnlineTimes)
	dao.CommitOrPanic(tx)

	response.WriteEntity(UserList{Users: userList})
}

func (ctx *context) getById(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")

	tx := dao.BeginOrPanic(ctx.db)
	user := dao.GetUser(tx, uid, ctx.lastOnlineTimes)
	dao.CommitOrPanic(tx)

	if user == nil {
		response.WriteHeader(404)
	} else {
		response.WriteEntity(user)
	}
}

// Returns nil if required params are missing or invalid
func readUserParams(request *restful.Request) *UserWritable {
	params := new(UserWritable)
	err := request.ReadEntity(params)
	if err != nil || params.FirstName == "" || params.LastName == "" {
		return nil
	}
	return params
}

// Returns params.TagId if set
// Otherwise, generates a default tag id
func getTagId(params *UserWritable) string {
	if params.TagId != "" {
		return params.TagId
	} else {
		return strings.ToLower(params.FirstName + params.LastName)
	}
}

func insertOrUpdate(tx *sql.Tx, user *User) error {
	if dao.UserExists(tx, user.Id) {
		return dao.UpdateUser(tx, user)
	} else {
		return dao.InsertUser(tx, user)
	}
}

func (ctx *context) updateUser(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	params := readUserParams(request)
	if params == nil {
		response.WriteErrorString(400, "Request body must have \"firstName\" and \"lastName\" keys")
		return
	}

	newUser := &User{
		Id:        uid,
		FirstName: params.FirstName,
		LastName:  params.LastName,
		Phone:     params.Phone,
		TagId:     getTagId(params),
	}

	tx := dao.BeginOrPanic(ctx.db)
	err := insertOrUpdate(tx, newUser)
	if errors.UniqueConstraintFailed(err) {
		if newUser.TagId == params.TagId {
			// tag collision with given tag id
			response.WriteErrorString(409, "User with tagId "+params.TagId+" already exists")
			tx.Rollback()
			return
		} else {
			// tag collision with default tag id; append a random 32-bit number
			newUser.TagId = newUser.TagId + fmt.Sprintf("%v", rand.Uint32())
			err = insertOrUpdate(tx, newUser)
			errors.PanicAndRollbackOnErr(err, tx)
		}
	} else {
		errors.PanicAndRollbackOnErr(err, tx)
	}
	dao.CommitOrPanic(tx)

	response.WriteEntity(newUser)
	broadcast.SendUserEvent(ctx.broadcaster, uid)
}

func (ctx *context) getMessages(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	if !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	messages := dao.GetUserMessages(tx, caller, uid)
	dao.CommitOrPanic(tx)

	response.WriteEntity(MessageList{Messages: messages})
}

// Returns nil if any required params are missing or invalid
func readMessageParams(request *restful.Request) *MessageWritable {
	params := new(MessageWritable)
	err := request.ReadEntity(params)
	if err != nil || params.Body == "" {
		return nil
	}
	return params
}

func (ctx *context) newMessage(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	params := readMessageParams(request)
	if params == nil {
		response.WriteErrorString(400, "Missing \"body\" key")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	if !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	message := &Message{
		Time: time.Now(),
		Body: params.Body,
		From: caller,
		To:   uid,
	}
	message = dao.InsertMessage(tx, message)
	dao.CommitOrPanic(tx)

	response.WriteEntity(message)
	broadcast.SendUserMessageEvent(ctx.broadcaster, caller, uid)
}

func (ctx *context) getAvatar(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")

	tx := dao.BeginOrPanic(ctx.db)
	avatar := dao.GetAvatar(tx, uid)
	dao.CommitOrPanic(tx)

	if avatar == nil {
		response.WriteHeader(404)
	} else {
		response.Write(avatar)
	}
}

func (ctx *context) updateAvatar(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	if request.Request.ContentLength > MAX_AVATAR_SIZE {
		response.WriteErrorString(413, fmt.Sprint("Avatar size cannot exceed ", MAX_AVATAR_SIZE, " bytes"))
		return
	}
	bytes, err := ioutil.ReadAll(request.Request.Body)
	errors.PanicOnErr(err)

	tx := dao.BeginOrPanic(ctx.db)
	dao.UpdateAvatar(tx, uid, bytes)
	dao.CommitOrPanic(tx)

	broadcast.SendUserAvatarEvent(ctx.broadcaster, uid)
}

func (ctx *context) getReceipts(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	if !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	receipts := dao.GetUserReceipts(tx, caller, uid)
	dao.CommitOrPanic(tx)

	response.WriteEntity(LastReadReceiptList{LastRead: receipts})
}

// Returns nil if any required params are missing or invalid
func parseUpdateReceiptParams(request *restful.Request) *LastReadReceiptWritable {
	params := new(LastReadReceiptWritable)
	err := request.ReadEntity(params)
	if err != nil || params.MessageId == 0 {
		return nil
	}
	return params
}

func (ctx *context) updateReceipt(request *restful.Request, response *restful.Response) {
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	params := parseUpdateReceiptParams(request)
	if params == nil {
		response.WriteErrorString(400, "Request body must have \"messageId\" key")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	if !dao.UserMessageExists(tx, params.MessageId, caller, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	receipt := dao.SetReceipt(tx, caller, uid, params.MessageId)
	dao.CommitOrPanic(tx)

	response.WriteEntity(receipt)
	broadcast.SendUserMessageReadEvent(ctx.broadcaster, caller, uid)
}

func (ctx *context) getPinned(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	cids := dao.GetPinned(tx, caller)
	dao.CommitOrPanic(tx)

	response.WriteEntity(IdList{Ids: cids})
}

func (ctx *context) pinConvo(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	if !dao.UserExists(tx, cid) && !dao.GroupExists(tx, cid) {
		tx.Rollback()
		response.WriteHeader(404)
		return
	}
	dao.SetPinned(tx, caller, cid)
	dao.CommitOrPanic(tx)
}

func (ctx *context) unpinConvo(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	dao.SetUnpinned(tx, caller, cid)
	dao.CommitOrPanic(tx)
}

func (ctx *context) postTyping(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	if dao.UserExists(tx, cid) {
		broadcast.SendUserTypingEvent(ctx.broadcaster, caller, cid)
		return
	}
	group := dao.GetGroup(tx, cid)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	} else if !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	broadcast.SendGroupTypingEvent(ctx.broadcaster, caller, group.Id, group.Members)
	dao.CommitOrPanic(tx)
}