package main

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/errors"
	"database/sql"
	"github.com/emicklei/go-restful"
	"time"
)

type BotsResource struct {
	broadcaster broadcast.Broadcaster
	db          *sql.DB
}

//
// Route definitions
//

func BuildBotsRoutes(db *sql.DB, broadcaster broadcast.Broadcaster) *restful.WebService {
	b := BotsResource{
		broadcaster: broadcaster,
		db:          db,
	}
	ws := new(restful.WebService)
	ws.Filter(LogRequest)

	ws.Path("/bots").
		Doc("Manage bots").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON)

	//
	// path: /bots
	//

	ws.Route(ws.GET("").Filter(CheckUser).To(b.getAll).
		Doc("Get all bots' information").
		Returns(200, "All bots", BotList{}).
		Returns(401, "Invalid authorization", nil))

	ws.Route(ws.POST("").Filter(CheckUser).To(b.newBot).
		Doc("Create a new bot").
		Reads(BotWritable{}).
		Returns(200, "Bot info", Bot{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Group does not exist", nil))

	//
	// path: /bots/{bid}
	//

	ws.Route(ws.POST("/{bid}").To(b.newMessage).
		Doc("Send message from bot").
		Param(ws.PathParameter("bid", "Bot id").DataType("string")).
		Reads(MessageWritable{}).
		Returns(200, "Message sent", Message{}).
		Returns(400, "Missing required key", nil).
		Returns(404, "Not found", nil))

	return ws
}

func (b BotsResource) getAll(request *restful.Request, response *restful.Response) {
	botList := make([]Bot, 0)
	rows, err := b.db.Query("SELECT id, name, group_id FROM bots")
	errors.PanicOnErr(err)
	for rows.Next() {
		var bot Bot
		err := rows.Scan(&bot.Id, &bot.Name, &bot.GroupId)
		errors.PanicOnErr(err)
		botList = append(botList, bot)
	}
	response.WriteEntity(BotList{Bots: botList})
}

func (b BotsResource) newBot(request *restful.Request, response *restful.Response) {
	// read params
	params := new(BotWritable)
	err := request.ReadEntity(params)
	if err != nil || params.Name == "" || params.GroupId == "" {
		response.WriteErrorString(400, "Request body must have \"name\" and \"groupId\" keys")
		return
	}
	// generate id
	id, err := generateRandomId()
	errors.PanicOnErr(err)
	// start transaction
	tx := BeginOrPanic(b.db)
	// ensure group exists
	err = tx.QueryRow("SELECT 1 FROM groups WHERE id=?", params.GroupId).Scan(new(int))
	if err == sql.ErrNoRows {
		tx.Rollback()
		response.WriteErrorString(404, "No group with id "+params.GroupId)
		return
	}
	errors.PanicAndRollbackOnErr(err, tx)
	// create bot
	_, err = tx.Exec("INSERT INTO bots (id, name, group_id) VALUES (?,?,?)",
		id,
		params.Name,
		params.GroupId,
	)
	errors.PanicAndRollbackOnErr(err, tx)
	// end transaction
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(Bot{
		Id:      id,
		Name:    params.Name,
		GroupId: params.GroupId,
	})
	// broadcast event
	broadcastBotEvent(b.broadcaster, id)
}

func (b BotsResource) newMessage(request *restful.Request, response *restful.Response) {
	// read params
	bid := request.PathParameter("bid")
	params := new(MessageWritable)
	err := request.ReadEntity(params)
	if err != nil || params.Body == "" {
		response.WriteErrorString(400, "Request body must have \"body\" key")
		return
	}
	// start transaction
	tx := BeginOrPanic(b.db)
	// ensure bot exists and get group id
	var gid string
	err = tx.QueryRow("SELECT group_id FROM bots WHERE id=?", bid).Scan(&gid)
	if err == sql.ErrNoRows {
		tx.Rollback()
		response.WriteErrorString(404, "No bot with id "+bid)
		return
	}
	errors.PanicAndRollbackOnErr(err, tx)
	// write msg to db
	msg := Message{
		Time: time.Now(),
		Body: params.Body,
		From: bid,
		To:   gid,
	}
	res, err := tx.Exec("INSERT INTO messages (time,body,from_id,to_id) VALUES (?,?,?,?)",
		msg.Time.UnixNano(),
		msg.Body,
		msg.From,
		msg.To)
	errors.PanicAndRollbackOnErr(err, tx)
	// get message id
	msg.Id, err = res.LastInsertId()
	errors.PanicAndRollbackOnErr(err, tx)
	// end transaction
	CommitOrPanic(tx)
	// write response
	response.WriteEntity(msg)
	// broadcast event
	broadcastGroupMessageEvent(b.broadcaster, gid)
}
