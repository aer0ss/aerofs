package convos

import (
	"aerofs.com/sloth/aeroclients/lipwig"
	"aerofs.com/sloth/aeroclients/sparta"
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/commands"
	"aerofs.com/sloth/dao"
	. "aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/lastOnline"
	"aerofs.com/sloth/push"
	. "aerofs.com/sloth/structs"
	"aerofs.com/sloth/util/asynccache"
	"database/sql"
	"errors"
	"fmt"
	"github.com/emicklei/go-restful"
	"log"
	"strconv"
	"time"
)

type context struct {
	broadcaster     broadcast.Broadcaster
	db              *sql.DB
	lastOnlineTimes *lastOnline.Times
	pushNotifier    push.Notifier
	commandHandler  *commands.Handler
	spartaClient    *sparta.Client
	lipwigClient    *lipwig.Client
	sidMap          asynccache.Map
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
	spartaClient *sparta.Client,
	lipwigClient *lipwig.Client,

) *restful.WebService {
	ctx := &context{
		broadcaster:     broadcaster,
		db:              db,
		lastOnlineTimes: lastOnlineTimes,
		pushNotifier:    pushNotifier,
		commandHandler:  commands.NewHandler(db),
		spartaClient:    spartaClient,
		lipwigClient:    lipwigClient,
		sidMap:          asynccache.New(createSharedFolderFunc(db, spartaClient, lipwigClient)),
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
	// path: /convos/{cid}/share
	//

	ws.Route(ws.POST("/{cid}/share").To(ctx.createShare).
		Doc("Create shared folder bound to convo").
		Param(ws.PathParameter("cid", "Convo id").DataType("string")).
		Consumes("*/*").
		Returns(200, "Convo info", Convo{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Convo does not exist", nil).
		Returns(409, "Convo already has a shared folder", nil))

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
		Param(ws.QueryParameter("since", "Id of last message received").DataType("int")).
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

func (ctx *context) createShare(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")

	// Determine if convo exists
	convo := getConvo(ctx.db, cid)
	if convo == nil {
		response.WriteHeader(404)
		return
	}

	r := <-ctx.sidMap.Get(cid)
	PanicOnErr(r.Error)

	var targets []string
	if !convo.IsPublic {
		targets = convo.Members
	}
	broadcast.SendConvoEvent(ctx.broadcaster, cid, targets)
}

func (ctx *context) addMember(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil || !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}
	// exit early if the user is already in the convo
	if convo.HasMember(uid) {
		return
	}
	dao.InsertMember(tx, cid, uid)
	dao.InsertMemberAddedMessage(tx, cid, uid, caller, time.Now())
	dao.CommitOrPanic(tx)

	convo.AddMember(uid) // ensure newly-added uid is in the list
	broadcastConvo(ctx.broadcaster, convo)
	broadcastMessage(ctx.broadcaster, convo)
}

func (ctx *context) removeMember(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	uid := request.PathParameter("uid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil || !dao.UserExists(tx, uid) {
		response.WriteHeader(404)
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}
	// exit early if the user is not in the convo
	if !convo.HasMember(uid) {
		return
	}
	dao.RemoveMember(tx, cid, uid)
	dao.InsertMemberRemovedMessage(tx, cid, uid, caller, time.Now())
	dao.CommitOrPanic(tx)

	broadcastConvo(ctx.broadcaster, convo)
	broadcastMessage(ctx.broadcaster, convo)
}

func (ctx *context) getMessages(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	since := request.QueryParameter("since")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil {
		response.WriteHeader(404)
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}

	var messages []Message
	if since == "" {
		messages = dao.GetMessages(tx, cid)
	} else {
		mid, err := strconv.Atoi(since)
		if err != nil {
			response.WriteErrorString(400, "\"since\" param must be a message id")
			return
		}
		messages = dao.GetMessagesSince(tx, cid, mid)
	}
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
	defer tx.Rollback()

	convo := dao.GetConvo(tx, cid, caller)
	callerUser := dao.GetUser(tx, caller, ctx.lastOnlineTimes)
	if convo == nil {
		response.WriteHeader(404)
		return
	}
	// check that caller is in convo
	if !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
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
	broadcastMessage(ctx.broadcaster, convo)
	pushRecipients := getPushRecipients(caller, convo.Members, ctx.lastOnlineTimes)
	go ctx.pushNotifier.NotifyNewMessage(callerUser, pushRecipients)
}

func (ctx *context) getReceipts(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil {
		response.WriteHeader(404)
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
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
	defer tx.Rollback()

	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil {
		response.WriteHeader(404)
		return
	}
	// check that caller is in convo or convo is public
	if !convo.IsPublic && !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}
	// check that the message exists in the conversation
	if !dao.MessageExists(tx, params.MessageId, cid) {
		response.WriteHeader(404)
		return
	}
	receipt := dao.SetReceipt(tx, caller, cid, params.MessageId)
	dao.CommitOrPanic(tx)

	response.WriteEntity(receipt)
	broadcastMessageRead(ctx.broadcaster, convo, caller, params.MessageId)
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

func broadcastConvo(bc broadcast.Broadcaster, convo *Convo) {
	targets := getBroadcastTargets(convo)
	broadcast.SendConvoEvent(bc, convo.Id, targets)
}

func broadcastMessage(bc broadcast.Broadcaster, convo *Convo) {
	targets := getBroadcastTargets(convo)
	broadcast.SendMessageEvent(bc, convo.Id, targets)
}

func broadcastMessageRead(bc broadcast.Broadcaster, convo *Convo, uid string, mid int64) {
	targets := getBroadcastTargets(convo)
	broadcast.SendMessageReadEvent(bc, convo.Id, uid, mid, targets)
}

func getBroadcastTargets(convo *Convo) []string {
	if !convo.IsPublic {
		return convo.Members
	}
	return nil
}

func createSharedFolderFunc(db *sql.DB, spartaClient *sparta.Client, lipwigClient *lipwig.Client) func(string) (string, error) {
	return func(cid string) (string, error) {
		c := getConvo(db, cid)
		if c == nil {
			return "", errors.New("Convo not found: " + cid)
		}
		if c.Sid != "" {
			return c.Sid, nil
		}
		var err error
		var sid string
		switch c.Type {
		case "CHANNEL":
			sid, err = spartaClient.CreateSharedFolder(c.Members, c.Name)
		case "DIRECT":
			sid, err = spartaClient.CreateLockedSharedFolder(c.Members, c.Name)
		default:
			return "", errors.New("cannot create shared folder for convo type " + c.Type)
		}
		if err != nil {
			return "", err
		}
		lipwigClient.SubscribeAndHandlePolaris(sid)
		setConvoSid(db, cid, sid)
		return sid, nil
	}
}

func getConvo(db *sql.DB, cid string) *Convo {
	tx := dao.BeginOrPanic(db)
	defer tx.Rollback()

	return dao.GetConvo(tx, cid, "")
}

func setConvoSid(db *sql.DB, cid, sid string) {
	log.Print("set ", cid, " sid = ", sid)
	tx := dao.BeginOrPanic(db)
	defer tx.Rollback()

	dao.SetConvoSid(tx, cid, sid)
	dao.CommitOrPanic(tx)
}
