package convos

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/commands"
	"aerofs.com/sloth/dao"
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
	commandHandler  *commands.Handler
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
		commandHandler:  commands.NewHandler(db),
	}
	ws := new(restful.WebService)
	ws.Filter(checkUser)
	ws.Filter(updateLastOnline)
	ws.Filter(filters.LogRequest)

	ws.Path("/convos").
		Doc("Manage convo membership, settings, and messages").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON)

	//
	// path: /convos
	//

	ws.Route(ws.GET("").To(ctx.getAll).
		Doc("Get a list of all joined and public convos").
		Returns(200, "A list of convos", ConvoList{}).
		Returns(401, "Invalid Authorization", nil))

	ws.Route(ws.POST("").To(ctx.createConvo).
		Doc("Create a new convo").
		Returns(200, "The newly-created convo", Convo{}).
		Returns(401, "Invalid Authorization", nil))

	//
	// path: /convos/{cid}
	//

	ws.Route(ws.GET("/{cid}").To(ctx.getById).
		Doc("Get convo info").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
		Returns(200, "Convo info", Convo{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(404, "Convo does not exist", nil))

	ws.Route(ws.PUT("/{cid}").To(ctx.updateConvo).
		Doc("Edit convo info").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
		Reads(GroupConvoWritable{}).
		Returns(200, "Convo info", Convo{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Convo does not exist", nil)) // N.B. use PATCH?

	//
	// path: /convos/{cid}/members/{uid}
	//

	ws.Route(ws.PUT("/{cid}/members/{uid}").To(ctx.addMember).
		Doc("Add user to convo").
		Notes("Returns 200 if user was already a member (noop)").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Successfully added", nil).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Convo or user not found", nil))

	ws.Route(ws.DELETE("/{cid}/members/{uid}").To(ctx.removeMember).
		Doc("Remove user from convo").
		Notes("Returns 200 if user was already not a member (noop)").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Successfully removed", nil). // N.B. return convo membership list?
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Convo not found", nil))

	//
	// path: /convos/{cid}/messages
	//

	ws.Route(ws.GET("/{cid}/messages").To(ctx.getMessages).
		Doc("Get all messages exchanged in a convo").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
		Returns(200, "A list of messages exchanged", MessageList{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Convo not found", nil))

	ws.Route(ws.POST("/{cid}/messages").To(ctx.newMessage).
		Doc("Send a new message to a convo").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
		Reads("string").
		Returns(200, "The newly-created message", Message{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Convo not found", nil))

	//
	// path: /convos/{cid}/receipts
	//

	ws.Route(ws.GET("/{cid}/receipts").To(ctx.getReceipts).
		Doc("Get last read receipt for all members of the conversation").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
		Returns(200, "Last-read receipts for all convo members", LastReadReceiptList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Convo not found", nil))

	ws.Route(ws.POST("/{cid}/receipts").To(ctx.updateReceipt).
		Doc("Set the last read message id for the conversation").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
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
	cs := dao.GetAllConvos(tx, caller)
	dao.CommitOrPanic(tx)
	response.WriteEntity(ConvoList{Convos: cs})
}

func (ctx *context) getById(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	cid := request.PathParameter("cid")

	tx := dao.BeginOrPanic(ctx.db)
	c := dao.GetConvo(tx, cid, caller)
	if c == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	dao.CommitOrPanic(tx)

	// check that caller is in convo or convo is public
	if !c.IsPublic && !c.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}

	response.WriteEntity(c)
}

func (ctx *context) addMember(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil || !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// exit early if the user is already in the convo
	if convo.HasMember(uid) {
		tx.Rollback()
		return
	}
	dao.InsertMember(tx, cid, uid)
	dao.InsertMemberAddedMessage(tx, cid, uid, caller, time.Now())
	dao.CommitOrPanic(tx)

	var targets []string
	if !convo.IsPublic {
		convo.AddMember(uid) // ensure newly-added uid is in the list
		targets = convo.Members
	}
	broadcast.SendConvoEvent(ctx.broadcaster, cid, targets)
	broadcast.SendMessageEvent(ctx.broadcaster, cid, targets)
}

func (ctx *context) removeMember(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil || !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// exit early if the user is not in the convo
	if !convo.HasMember(uid) {
		tx.Rollback()
		return
	}
	dao.RemoveMember(tx, cid, uid)
	dao.InsertMemberRemovedMessage(tx, cid, uid, caller, time.Now())
	dao.CommitOrPanic(tx)

	var targets []string
	if !convo.IsPublic {
		targets = convo.Members
	}
	broadcast.SendConvoEvent(ctx.broadcaster, cid, targets)
	broadcast.SendMessageEvent(ctx.broadcaster, cid, targets)
}

func (ctx *context) getMessages(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	messages := dao.GetMessages(tx, cid)
	dao.CommitOrPanic(tx)

	response.WriteEntity(MessageList{Messages: messages})
}

func readMessageParams(request *restful.Request) *MessageWritable {
	params := new(MessageWritable)
	err := request.ReadEntity(params)
	if err != nil || params.Body == "" {
		return nil
	}
	return params
}

func (ctx *context) newMessage(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	params := readMessageParams(request)
	if params == nil {
		response.WriteErrorString(400, "Missing \"body\" key")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	convo := dao.GetConvo(tx, cid, caller)
	callerUser := dao.GetUser(tx, caller, ctx.lastOnlineTimes)
	if convo == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in convo
	if !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}

	message := &Message{
		Time: time.Now(),
		Body: params.Body,
		From: caller,
		To:   cid,
	}

	// Parse for slash commands
	// If a slash command, create a message for the command response, not the input
	if ctx.commandHandler.IsSlashCommand(message.Body) {
		// Perform request
		cmd, response, err := ctx.commandHandler.HandleCommand(message.From, message.To, message.Body)
		if err != nil {
			response = fmt.Sprintf("Unable to execute slash command %s. %s", cmd, err)
		}

		message = dao.InsertCommandExecutedMessage(tx, cmd, cid, response)
	} else {
		// Insert regular message
		message = dao.InsertMessage(tx, message)
	}

	dao.CommitOrPanic(tx)
	response.WriteEntity(message)
	broadcast.SendMessageEvent(ctx.broadcaster, cid, convo.Members)
	pushRecipients := getPushRecipients(caller, convo.Members, ctx.lastOnlineTimes)
	go ctx.pushNotifier.NotifyNewMessage(callerUser, pushRecipients)
}

func (ctx *context) getReceipts(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	receipts := dao.GetReceipts(tx, cid)
	dao.CommitOrPanic(tx)

	response.WriteEntity(LastReadReceiptList{LastRead: receipts})
}

func (ctx *context) updateReceipt(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	params := new(LastReadReceiptWritable)
	err := request.ReadEntity(params)
	if err != nil || params.MessageId == 0 {
		response.WriteErrorString(400, "Request body must have \"messageId\" key")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		tx.Rollback()
		return
	}
	// check that the message exists in the conversation
	if !dao.MessageExists(tx, params.MessageId, cid) {
		response.WriteHeader(404)
		tx.Rollback()
		return
	}
	receipt := dao.SetReceipt(tx, caller, cid, params.MessageId)
	dao.CommitOrPanic(tx)

	response.WriteEntity(receipt)
	broadcast.SendMessageReadEvent(ctx.broadcaster, cid, convo.Members)
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
