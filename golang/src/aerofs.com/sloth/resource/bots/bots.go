package bots

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/filters"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"github.com/emicklei/go-restful"
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

	return ws
}

func (ctx *context) getAll(request *restful.Request, response *restful.Response) {
	tx := dao.BeginOrPanic(ctx.db)
	botList := dao.GetAllBots(tx)
	dao.CommitOrPanic(tx)
	response.WriteEntity(BotList{Bots: botList})
}

func (ctx *context) newBot(request *restful.Request, response *restful.Response) {
	// read params
	params := new(BotWritable)
	err := request.ReadEntity(params)
	if err != nil || params.Name == "" || params.ConvoId == "" {
		response.WriteErrorString(400, "Request body must have \"name\" and \"convoId\" keys")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	if !dao.ConvoExists(tx, params.ConvoId) {
		tx.Rollback()
		response.WriteErrorString(404, "No convo with id "+params.ConvoId)
		return
	}
	bot := dao.NewBot(tx, params.Name, params.ConvoId)
	dao.CommitOrPanic(tx)

	response.WriteEntity(bot)
	broadcast.SendBotEvent(ctx.broadcaster, bot.Id)
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
	bid := request.PathParameter("bid")

	params := readMessageParams(request)
	if params == nil {
		response.WriteErrorString(400, "Request body must have \"body\" key")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	// ensure bot exists and get convo id
	bot := dao.GetBot(tx, bid)
	if bot == nil {
		tx.Rollback()
		response.WriteErrorString(404, "No bot with id "+bid)
		return
	}

	msg := &Message{
		Time: time.Now(),
		Body: params.Body,
		From: bid,
		To:   bot.ConvoId,
	}
	msg = dao.InsertMessage(tx, msg)
	members := dao.GetMembers(tx, bot.ConvoId)
	dao.CommitOrPanic(tx)

	response.WriteEntity(msg)
	broadcast.SendMessageEvent(ctx.broadcaster, bot.ConvoId, members)
}
