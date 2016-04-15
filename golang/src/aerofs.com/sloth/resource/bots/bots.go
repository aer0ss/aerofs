package bots

import (
	"aerofs.com/sloth/bots"
	"aerofs.com/sloth/broadcast"
	. "aerofs.com/sloth/constants"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"github.com/emicklei/go-restful"
	"io/ioutil"
	"time"
)

type context struct {
	broadcaster broadcast.Broadcaster
	db          *sql.DB
}

//
// Route definitions
//

func BuildRoutes(db *sql.DB, broadcaster broadcast.Broadcaster, checkUserFilter restful.FilterFunction) *restful.WebService {
	ctx := &context{
		broadcaster: broadcaster,
		db:          db,
	}
	ws := new(restful.WebService)
	ws.Filter(filters.LogRequest)

	ws.Path("/bots").
		Doc("Manage bots").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON)

	//
	// path: /bots
	//

	ws.Route(ws.GET("").Filter(checkUserFilter).To(ctx.getAll).
		Doc("Get all bots' information").
		Returns(200, "All bots", BotList{}).
		Returns(401, "Invalid authorization", nil))

	ws.Route(ws.POST("").Filter(checkUserFilter).To(ctx.newBot).
		Doc("Create a new bot").
		Reads(BotWritable{}).
		Returns(200, "Bot info", Bot{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Convo does not exist", nil))

	//
	// path: /bots/{bid}
	//

	ws.Route(ws.POST("/{bid}").To(ctx.newMessage).
		Doc("Send message from bot").
		Param(ws.PathParameter("bid", "Bot id").DataType("string")).
		Reads(MessageWritable{}).
		Returns(200, "Message sent", Message{}).
		Returns(400, "Missing required key", nil).
		Returns(404, "Not found", nil))

	ws.Route(ws.PUT("/{bid}").To(ctx.updateBotProfile).
		Doc("Update a bot's profile").
		Param(ws.PathParameter("bid", "Bot id").DataType("string")).
		Reads(BotWritable{}).
		Returns(200, "Bot updated", Message{}).
		Returns(400, "Missing required key", nil).
		Returns(404, "Not found", nil))

	//
	// path: /bots/{bid}/avatar
	//

	ws.Route(ws.GET("/{bid}/avatar").To(ctx.getAvatar).
		Doc("Retrieve a bot user's avatar").
		Param(ws.PathParameter("bid", "Bot id").DataType("string")).
		Returns(200, "This returns the raw avatar data. It should be hot-linkable in an img tag", "bytestring").
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Bot does not exist", nil))

	ws.Route(ws.PUT("/{bid}/avatar").To(ctx.updateAvatar).
		Doc("Update a bot's avatar").
		Notes("This expects the raw avater date in the body").
		Consumes(restful.MIME_OCTET).
		Param(ws.PathParameter("bid", "Bot id").DataType("string")).
		Reads("bytestring").
		Returns(200, "Bot avatar updated", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Bot does not exist", nil))

	return ws
}

// Retrieve a list of all bots
func (ctx *context) getAll(request *restful.Request, response *restful.Response) {
	tx := dao.BeginOrPanic(ctx.db)
	botList := dao.GetAllBots(tx)
	dao.CommitOrPanic(tx)
	response.WriteEntity(BotList{Bots: botList})
}

// Create a new bot
func (ctx *context) newBot(request *restful.Request, response *restful.Response) {
	params := new(BotWritable)
	err := request.ReadEntity(params)
	if err != nil || params.Name == "" || params.ConvoId == "" {
		response.WriteErrorString(400, "Request body must have \"name\" and \"convoId\" keys")
		return
	}

	// Verify conversation exists
	tx := dao.BeginOrPanic(ctx.db)
	if !dao.ConvoExists(tx, params.ConvoId) {
		tx.Rollback()
		response.WriteErrorString(404, "No convo with id "+params.ConvoId)
		return
	}

	// Create the bot
	bot := dao.NewBot(tx, params.Name, params.ConvoId, params.CreatorId, params.Type)
	dao.CommitOrPanic(tx)

	response.WriteEntity(bot)
	broadcast.SendBotEvent(ctx.broadcaster, bot.Id)
}

// Update a bot's profile
func (ctx *context) updateBotProfile(request *restful.Request, response *restful.Response) {
	// Parse request
	bid := request.PathParameter("bid")
	params := new(BotWritable)
	err := request.ReadEntity(params)
	if err != nil || params.Name == "" {
		response.WriteErrorString(400, "Request body must have \"body\" key")
	}
	newName := params.Name

	// Verify bot existence
	tx := dao.BeginOrPanic(ctx.db)
	bot := dao.GetBot(tx, bid)
	if bot == nil {
		tx.Rollback()
		response.WriteErrorString(404, "No bot with id "+bid)
	}

	// Update bot
	dao.UpdateBot(tx, bid, newName)
	dao.CommitOrPanic(tx)
	bot.Name = newName

	response.WriteEntity(bot)
	broadcast.SendBotEvent(ctx.broadcaster, bot.Id)
}

func (ctx *context) getAvatar(request *restful.Request, response *restful.Response) {
	// Parse request
	bid := request.PathParameter("bid")

	// Verify bot existence
	tx := dao.BeginOrPanic(ctx.db)
	bot := dao.GetBot(tx, bid)
	if bot == nil {
		tx.Rollback()
		response.WriteErrorString(404, "No bot with id "+bid)
	}

	// Get avatar
	avatar := dao.GetBotAvatar(tx, bid)
	dao.CommitOrPanic(tx)

	if avatar == nil {
		response.WriteHeader(404)
	} else {
		response.Write(avatar)
	}
}

// Update a bot user's avatar
func (ctx *context) updateAvatar(request *restful.Request, response *restful.Response) {
	// Parse request
	bid := request.PathParameter("bid")
	if request.Request.ContentLength > MAX_AVATAR_SIZE {
		response.WriteErrorString(413, "AVATAR size cannot exceed "+string(MAX_AVATAR_SIZE)+" bytes")
		return
	}

	// Verify bot exists
	tx := dao.BeginOrPanic(ctx.db)
	bot := dao.GetBot(tx, bid)
	if bot == nil {
		tx.Rollback()
		response.WriteErrorString(404, "No bot with id "+bid)
	}

	// Insert avatar
	avatar, err := ioutil.ReadAll(request.Request.Body)
	errors.PanicOnErr(err)
	dao.UpdateBotAvatar(tx, bid, avatar)
	dao.CommitOrPanic(tx)

	broadcast.SendBotAvatarEvent(ctx.broadcaster, bot.Id)
}

// Send a message from a bot
func (ctx *context) newMessage(request *restful.Request, response *restful.Response) {
	bid := request.PathParameter("bid")

	// Verify bot exists
	tx := dao.BeginOrPanic(ctx.db)
	bot := dao.GetBot(tx, bid)
	if bot == nil {
		tx.Rollback()
		response.WriteErrorString(404, "No bot with id "+bid)
		return
	}

	// Pass bot to common bot handler which should return a (string, error)
	body, err := ioutil.ReadAll(request.Request.Body)
	errors.PanicOnErr(err)
	message, err := botdriver.TransformBotMessage(bot.Type, body, request.Request.Header)
	errors.PanicOnErr(err)

	// Create message
	msg := &Message{
		Time:   time.Now(),
		Body:   string(message),
		From:   bid,
		To:     bot.ConvoId,
		IsData: true,
	}
	msg = dao.InsertMessage(tx, msg)
	members := dao.GetMembers(tx, bot.ConvoId)
	dao.CommitOrPanic(tx)

	// Broadcast message
	response.WriteEntity(msg)
	broadcast.SendMessageEvent(ctx.broadcaster, bot.ConvoId, members)
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
