package convos

import (
	"aerofs.com/sloth/aeroclients/lipwig"
	"aerofs.com/sloth/aeroclients/polaris"
	"aerofs.com/sloth/aeroclients/sparta"
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/commands"
	"aerofs.com/sloth/dao"
	. "aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/index"
	"aerofs.com/sloth/lastOnline"
	"aerofs.com/sloth/push"
	. "aerofs.com/sloth/structs"
	"aerofs.com/sloth/util"
	"aerofs.com/sloth/util/asynccache"
	"database/sql"
	"errors"
	"fmt"
	"github.com/emicklei/go-restful"
	"log"
	"sort"
	"strconv"
	"strings"
	"time"
)

type context struct {
	broadcaster     broadcast.Broadcaster
	db              *sql.DB
	lastOnlineTimes *lastOnline.Times
	pushNotifier    push.Notifier
	commandHandler  *commands.Handler
	spartaClient    *sparta.Client
	polarisClient   *polaris.Client
	lipwigClient    *lipwig.Client
	sidMap          asynccache.Map
	idx             *index.Index
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
	polarisClient *polaris.Client,
	lipwigClient *lipwig.Client,
	idx *index.Index,
) *restful.WebService {
	ctx := &context{
		broadcaster:     broadcaster,
		db:              db,
		lastOnlineTimes: lastOnlineTimes,
		pushNotifier:    pushNotifier,
		commandHandler:  commands.NewHandler(db),
		spartaClient:    spartaClient,
		lipwigClient:    lipwigClient,
		polarisClient:   polarisClient,
		sidMap:          asynccache.New(createSharedFolderFunc(db, spartaClient, lipwigClient)),
		idx:             idx,
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
		Param(ws.QueryParameter("after", "Message ID").DataType("int")).
		Param(ws.QueryParameter("before", "Message ID").DataType("int")).
		Param(ws.QueryParameter("limit", "Message ID").DataType("int")).
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
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	cid := request.PathParameter("cid")

	// Determine if convo exists
	convo := getConvo(ctx.db, cid)
	if convo == nil {
		response.WriteHeader(404)
		return
	}
	log.Printf("Creating a shared folder for convo %v and caller %v", cid, caller)
	r := <-ctx.sidMap.Get(cid, caller)
	PanicOnErr(r.Error)

	convo = getConvo(ctx.db, cid)
	var targets []string
	if !convo.IsPublic {
		targets = convo.Members
	}

	response.WriteEntity(convo)
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
	convo.AddMember(uid) // ensure newly-added uid is in the list

	// NOTE: Casted to GroupConvoWritable to reuse the current update functionality
	updatedConvo := &GroupConvoWritable{
		Members:  append(convo.Members, uid),
		Name:     &convo.Name,
		IsPublic: &convo.IsPublic,
	}
	convo, dependentConvos := dao.UpdateConvo(tx, convo, updatedConvo, caller)

	if convo.Sid != "" {
		go func() {
			log.Printf("adding %v to %v on sparta\n", uid, convo.Sid)
			err := ctx.spartaClient.AddSharedFolderMember(convo.Sid, uid, caller)
			if err != nil {
				log.Printf("err adding %v to sid %v: %v\n", uid, convo.Sid, err)
			}
		}()
	}

	convo.AddMember(uid) // ensure newly-added uid is in the list

	// Insert added message to Root folder
	dao.InsertMemberAddedMessage(tx, cid, uid, caller, time.Now())

	// Notify all dependent convos of new member
	// Notify just the root convo of the new message
	for _, cid := range dependentConvos {
		convo := dao.GetConvo(tx, cid, caller)
		broadcastConvo(ctx.broadcaster, convo)
	}
	dao.CommitOrPanic(tx)
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
	// Get updated members
	var newMembers []string
	for i, member := range convo.Members {
		if member == uid {
			newMembers = append(convo.Members[:i], convo.Members[i+1:]...)
			break
		}
	}

	// NOTE: Casted to GroupConvoWritable to reuse the current update functionality
	updatedConvo := &GroupConvoWritable{
		Members:  newMembers,
		Name:     &convo.Name,
		IsPublic: &convo.IsPublic,
	}
	convo, dependentConvos := dao.UpdateConvo(tx, convo, updatedConvo, caller)

	// Only root folder gets removed message
	dao.InsertMemberRemovedMessage(tx, cid, uid, caller, time.Now())

	if convo.Sid != "" {
		go func() {
			log.Printf("removing %v from %v on sparta\n", uid, convo.Sid)
			err := ctx.spartaClient.RemoveSharedFolderMember(convo.Sid, uid, caller)
			if err != nil {
				log.Printf("err removing %v from sid %v: %v\n", uid, convo.Sid, err)
			}
		}()
	}

	// Notify dependents of convo update
	// Notify root if new message
	for _, cid := range dependentConvos {
		depConvo := dao.GetConvo(tx, cid, caller)
		broadcastConvo(ctx.broadcaster, depConvo)
	}

	dao.CommitOrPanic(tx)
	broadcastMessage(ctx.broadcaster, convo)
}

func (ctx *context) getMessages(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	cid := request.PathParameter("cid")
	afterParam := request.QueryParameter("after")
	beforeParam := request.QueryParameter("before")
	limitParam := request.QueryParameter("limit")

	var after, before, limit int
	var err error
	if afterParam != "" {
		after, err = strconv.Atoi(afterParam)
		if err != nil {
			log.Print(err)
			response.WriteErrorString(400, "\"after\" param must be a message id")
			return
		}
	}
	if beforeParam != "" {
		before, err = strconv.Atoi(beforeParam)
		if err != nil {
			log.Print(err)
			response.WriteErrorString(400, "\"before\" param must be a message id")
			return
		}
	}
	if limitParam != "" {
		limit, err = strconv.Atoi(limitParam)
		if err != nil || limit < 1 {
			log.Print(err)
			response.WriteErrorString(400, "\"limit\" param must be a positive integer")
			return
		}
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

	messages := dao.GetMessages(tx, cid, before, after, limit)
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

	var pushRecipients []string
	if convo.Type == "DIRECT" {
		// for direct convos, notify the other member
		var other string
		if convo.Members[0] == caller {
			other = convo.Members[1]
		} else {
			other = convo.Members[0]
		}
		pushRecipients = append(pushRecipients, other)
	} else {
		// for non-direct convos, only notify tagged members
		tags := dao.GetConvoTagIds(tx, cid)
		for tagId, uid := range tags {
			if uid == caller || !ctx.lastOnlineTimes.IsOffline(uid) {
				continue
			}
			if *dao.GetSettings(tx, uid).NotifyOnlyOnTag && !util.IsTagPresent(message.Body, tagId) {
				continue
			}
			pushRecipients = append(pushRecipients, uid)
		}
	}

	dao.CommitOrPanic(tx)
	response.WriteEntity(message)
	broadcastMessage(ctx.broadcaster, convo)
	go ctx.pushNotifier.NotifyNewMessage(callerUser, pushRecipients, convo)
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

func createSharedFolderFunc(db *sql.DB, spartaClient *sparta.Client, lipwigClient *lipwig.Client) func(string, ...interface{}) (string, error) {
	return func(cid string, args ...interface{}) (string, error) {
		log.Printf("CreateSharedFolder %v", args)
		owner, ok := args[0].(string)
		if !ok {
			return "", errors.New("The owner argument for the new shared folder is not a string")
		}
		c := getConvo(db, cid)
		if c == nil {
			return "", errors.New("Convo not found: " + cid)
		}
		log.Print("maybe create share for ", cid)
		if c.Sid != "" {
			log.Printf("found sid %v for %v\n", c.Sid, cid)
			return c.Sid, nil
		}
		log.Print("creating share for ", cid)
		var err error
		var sid string

		switch c.Type {
		case "CHANNEL":
			sid, err = spartaClient.CreateSharedFolder(c.Members, owner, c.Name)
		case "DIRECT":
			sharedFolderName := getSharedFolderName(db, cid)
			sid, err = spartaClient.CreateLockedSharedFolder(c.Members, owner, sharedFolderName)
		default:
			return "", errors.New("cannot create shared folder for convo type " + c.Type)
		}
		if err != nil {
			return "", err
		}
		log.Printf("created share %v for %v\n", sid, cid)
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

// Return a name for a new shared folder
// It concatenates the names of all participants, delimited by commas
func getSharedFolderName(db *sql.DB, cid string) string {
	tx := dao.BeginOrPanic(db)
	defer tx.Rollback()
	firstNames := dao.GetMembersFirstNames(tx, cid)
	sort.Strings(firstNames)
	return strings.Join(firstNames, ", ")
}
