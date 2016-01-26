package commands

import (
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"fmt"
	"github.com/emicklei/go-restful"
)

type context struct {
	db *sql.DB
}

func BuildRoutes(
	db *sql.DB,
	checkUser restful.FilterFunction,
) *restful.WebService {

	ctx := &context{
		db: db,
	}

	ws := new(restful.WebService)
	ws.Filter(checkUser)
	ws.Filter(filters.LogRequest)
	ws.Path("/commands").
		Doc("Manage list of created commands").
		Consumes(restful.MIME_JSON).
		Produces(restful.MIME_JSON)

	//
	// path: /commands
	//

	ws.Route(ws.GET("").To(ctx.getAllCommands).
		Doc("Get all registered slash commands").
		Returns(200, "A list of commands", CommandList{}).
		Returns(401, "Invalid Authorization", nil))

	ws.Route(ws.POST("").To(ctx.createCommand).
		Doc("Create a new command").
		Reads(CommandWritable{}).
		Returns(200, "Command info", Command{}).
		Returns(401, "Invalid Authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(409, "Resource conflict", nil))

	return ws
}

//
// Handlers
//

func (ctx *context) getAllCommands(request *restful.Request, response *restful.Response) {
	tx := dao.BeginOrPanic(ctx.db)
	commands := dao.GetAllCommands(tx)
	dao.CommitOrPanic(tx)

	response.WriteEntity(CommandList{Commands: commands})
}

// Create a new command
func (ctx *context) createCommand(request *restful.Request, response *restful.Response) {

	// Check if valid command
	params := readCommandParams(request)
	if params == nil {
		response.WriteErrorString(400, `Request body must have "command", "method", "url", "token" keys`)
		return
	}
	// Check if command exists
	tx := dao.BeginOrPanic(ctx.db)
	if dao.CommandExists(tx, params.Command) {
		tx.Rollback()
		response.WriteErrorString(409, fmt.Sprintf(`Command "%s" already exists`, params.Command))
		return
	}

	newCommand := &Command{
		Command:     params.Command,
		Method:      params.Method,
		URL:         params.URL,
		Token:       params.Token,
		Syntax:      params.Syntax,
		Description: params.Description,
	}
	// Insert New Command
	err := dao.InsertCommand(tx, newCommand)
	errors.PanicAndRollbackOnErr(err, tx)
	dao.CommitOrPanic(tx)
	response.WriteEntity(newCommand)
}

//
// Helper
//

// Returns nil if required params are missing or invalid
func readCommandParams(request *restful.Request) *CommandWritable {
	params := new(CommandWritable)
	err := request.ReadEntity(params)
	if err != nil || isCommandMissingFields(params) {
		return nil
	}
	return params
}

// Return if the command has all necessary fields
// TODO : Improve sanitzation
func isCommandMissingFields(c *CommandWritable) bool {
	return c.Command == "" || c.Method == "" || c.URL == "" || c.Token == ""
}
