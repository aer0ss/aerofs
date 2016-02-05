package groups

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/commands"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/lastOnline"
	"aerofs.com/sloth/push"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"fmt"
	"github.com/emicklei/go-restful"
	"time"
)

type context struct {
	broadcaster     broadcast.Broadcaster
	db              *sql.DB
	lastOnlineTimes *lastOnline.Times
	pushNotifier    push.Notifier
}

//
// Route definitions
//

func BuildRoutes(
	db *sql.DB,
	broadcaster broadcast.Broadcaster,
	lastOnlineTimes *lastOnline.Times,
	pushNotifier push.Notifier,
	checkUser restful.FilterFunction,
	updateLastOnline restful.FilterFunction,

) *restful.WebService {
	ctx := &context{
		broadcaster:     broadcaster,
		db:              db,
		lastOnlineTimes: lastOnlineTimes,
		pushNotifier:    pushNotifier,
	}
	ws := new(restful.WebService)
	ws.Filter(checkUser)
	ws.Filter(updateLastOnline)
	ws.Filter(filters.LogRequest)

	ws.Path("/groups").
		Doc("Manage group membership, settings, and messages").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON)

	//
	// path: /groups
	//

	ws.Route(ws.GET("").To(ctx.getAll).
		Doc("Get a list of all joined and public groups").
		Returns(200, "A list of groups", GroupList{}).
		Returns(401, "Invalid Authorization", nil))

	ws.Route(ws.POST("").To(ctx.createGroup).
		Doc("Create a new group").
		Reads(GroupWritable{}).
		Returns(200, "The newly-created group", Group{}).
		Returns(401, "Invalid Authorization", nil))

	//
	// path: /groups/{gid}
	//

	ws.Route(ws.GET("/{gid}").To(ctx.getById).
		Doc("Get group info").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "Group info", Group{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(404, "Group does not exist", nil))

	ws.Route(ws.PUT("/{gid}").To(ctx.updateGroup).
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

	ws.Route(ws.GET("/{gid}/members").To(ctx.getMembers).
		Doc("Get group members' ids").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "Group member ids", IdList{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group does not exist", nil))

	//
	// path: /groups/{gid}/members/{uid}
	//

	ws.Route(ws.PUT("/{gid}/members/{uid}").To(ctx.addMember).
		Doc("Add user to group").
		Notes("Returns 200 if user was already a member (noop)").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Successfully added", nil). // N.B. return group membership list?
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group or user not found", nil))

	ws.Route(ws.DELETE("/{gid}/members/{uid}").To(ctx.removeMember).
		Doc("Remove user from group").
		Notes("Returns 200 if user was already not a member (noop)").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Successfully removed", nil). // N.B. return group membership list?
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group not found", nil))

	//
	// path: /groups/{gid}/messages
	//

	ws.Route(ws.GET("/{gid}/messages").To(ctx.getMessages).
		Doc("Get all messages exchanged in a group").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "A list of messages exchanged", MessageList{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group not found", nil))

	ws.Route(ws.POST("/{gid}/messages").To(ctx.newMessage).
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

	ws.Route(ws.GET("/{gid}/receipts").To(ctx.getReceipts).
		Doc("Get last read receipt for all members of the conversation").
		Param(ws.PathParameter("gid", "Group id").DataType("string")).
		Returns(200, "Last-read receipts for all group members", LastReadReceiptList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group not found", nil))

	ws.Route(ws.POST("/{gid}/receipts").To(ctx.updateReceipt).
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

func (ctx *context) getAll(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	tx := dao.BeginOrPanic(ctx.db)
	groups := dao.GetAllGroups(tx, caller)
	dao.CommitOrPanic(tx)
	response.WriteEntity(GroupList{Groups: groups})
}

// Returns nil if any required fields are missing or invalid
func readGroupParams(request *restful.Request) *GroupWritable {
	params := new(GroupWritable)
	err := request.ReadEntity(params)
	errors.PanicOnErr(err)
	if params.Name == nil || params.Members == nil || params.IsPublic == nil {
		return nil
	}
	return params
}

func (ctx *context) createGroup(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	params := readGroupParams(request)
	if params == nil {
		response.WriteErrorString(400, "Request body must have \"name\", \"is_public\", and \"members\" keys")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.CreateGroup(tx, params, caller)
	dao.CommitOrPanic(tx)

	response.WriteEntity(group)
	broadcast.SendGroupEvent(ctx.broadcaster, group.Id, group.Members)
	broadcast.SendGroupMessageEvent(ctx.broadcaster, group.Id, group.Members)
}

func (ctx *context) getById(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	dao.CommitOrPanic(tx)

	// check that caller is in group or group is public
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	if !group.IsPublic && !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}

	response.WriteEntity(group)
}

func (ctx *context) updateGroup(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	params := new(GroupWritable)
	err := request.ReadEntity(params)
	errors.PanicOnErr(err)

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	group = dao.UpdateGroup(tx, group, params, caller)
	dao.CommitOrPanic(tx)

	response.WriteEntity(group)
	var targets []string
	if !group.IsPublic {
		targets = group.Members
	}
	broadcast.SendGroupEvent(ctx.broadcaster, gid, targets)
	if params.Members != nil {
		broadcast.SendGroupMessageEvent(ctx.broadcaster, gid, targets)
	}
}

func (ctx *context) getMembers(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	dao.CommitOrPanic(tx)

	// check that caller is in group or group is public
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	if !group.IsPublic && !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}

	response.WriteEntity(IdList{Ids: group.Members})
}

func (ctx *context) addMember(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	if group == nil || !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// exit early if the user is already in the group
	if group.HasMember(uid) {
		tx.Rollback()
		return
	}
	dao.AddGroupMember(tx, gid, uid)
	dao.InsertGroupMemberAddedMessage(tx, gid, uid, caller, time.Now())
	dao.CommitOrPanic(tx)

	var targets []string
	if !group.IsPublic {
		group.AddMember(uid) // ensure newly-added uid is in the list
		targets = group.Members
	}
	broadcast.SendGroupEvent(ctx.broadcaster, gid, targets)
	broadcast.SendGroupMessageEvent(ctx.broadcaster, gid, targets)
}

func (ctx *context) removeMember(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	if group == nil || !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// exit early if the user is not in the group
	if !group.HasMember(uid) {
		tx.Rollback()
		return
	}
	dao.RemoveGroupMember(tx, gid, uid)
	dao.InsertGroupMemberRemovedMessage(tx, gid, uid, caller, time.Now())
	dao.CommitOrPanic(tx)

	var targets []string
	if !group.IsPublic {
		targets = group.Members
	}
	broadcast.SendGroupEvent(ctx.broadcaster, gid, targets)
	broadcast.SendGroupMessageEvent(ctx.broadcaster, gid, targets)
}

func (ctx *context) getMessages(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	messages := dao.GetGroupMessages(tx, gid)
	dao.CommitOrPanic(tx)

	response.WriteEntity(MessageList{Messages: messages})
}

// Returns nil if any required params are missing or invalid
// FIXME: this is duplicated exactly from resource/users/users.go
func readMessageParams(request *restful.Request) *MessageWritable {
	params := new(MessageWritable)
	err := request.ReadEntity(params)
	if err != nil || params.Body == "" {
		return nil
	}
	return params
}

func (ctx *context) newMessage(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	params := readMessageParams(request)
	if params == nil {
		response.WriteErrorString(400, "Missing \"body\" key")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	callerUser := dao.GetUser(tx, caller, ctx.lastOnlineTimes)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group
	if !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}

	message := &Message{
		Time: time.Now(),
		Body: params.Body,
		From: caller,
		To:   gid,
	}

	// Parse for slash commands
	// If a slash command, create a message for the command response, not the input
	h := commandhandler.NewCommandHandler(ctx.db)
	if h.IsSlashCommand(message.Body) {

		// Perform request
		cmd, response, err := h.HandleCommand(message.From, message.To, message.Body)
		if err != nil {
			response = fmt.Sprintf("Unable to execute slash command %s. %s", cmd, err)
		}

		// For each /command, the caller is the command string
		tx := dao.BeginOrPanic(ctx.db)
		caller := cmd
		message = dao.InsertGroupCommandMessage(tx, gid, caller, response, time.Now())
		dao.CommitOrPanic(tx)
	} else {
		// Insert regular message
		message = dao.InsertMessage(tx, message)
		dao.CommitOrPanic(tx)
	}

	response.WriteEntity(message)
	broadcast.SendGroupMessageEvent(ctx.broadcaster, gid, group.Members)
	pushRecipients := getPushRecipients(caller, group.Members, ctx.lastOnlineTimes)
	go ctx.pushNotifier.NotifyNewMessage(callerUser, pushRecipients)
}

func (ctx *context) getReceipts(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	receipts := dao.GetGroupReceipts(tx, gid)
	dao.CommitOrPanic(tx)

	response.WriteEntity(LastReadReceiptList{LastRead: receipts})
}

func (ctx *context) updateReceipt(request *restful.Request, response *restful.Response) {
	gid := request.PathParameter("gid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	params := new(LastReadReceiptWritable)
	err := request.ReadEntity(params)
	if err != nil || params.MessageId == 0 {
		response.WriteErrorString(400, "Request body must have \"messageId\" key")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	group := dao.GetGroup(tx, gid)
	if group == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in group or group is public
	if !group.IsPublic && !group.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// check that the message exists in the conversation
	if !dao.GroupMessageExists(tx, params.MessageId, gid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	receipt := dao.SetReceipt(tx, caller, gid, params.MessageId)
	dao.CommitOrPanic(tx)

	response.WriteEntity(receipt)
	broadcast.SendGroupMessageReadEvent(ctx.broadcaster, gid, group.Members)
}

func getPushRecipients(caller string, members []string, lastOnlineTimes *lastOnline.Times) []string {
	rs := make([]string, 0)
	for _, r := range members {
		if r == caller {
			continue
		}
		if lastOnlineTimes.IsOffline(r) {
			rs = append(rs, r)
		}
	}
	return rs
}
