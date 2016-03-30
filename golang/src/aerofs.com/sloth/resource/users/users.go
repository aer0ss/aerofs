package users

import (
	"aerofs.com/sloth/aeroclients/sparta"
	"aerofs.com/sloth/broadcast"
	. "aerofs.com/sloth/constants"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	"aerofs.com/sloth/lastOnline"
	"aerofs.com/sloth/push"
	. "aerofs.com/sloth/structs"
	"database/sql"
	"fmt"
	"github.com/emicklei/go-restful"
	"io/ioutil"
	"math/rand"
	"strings"
)

type context struct {
	broadcaster     broadcast.Broadcaster
	db              *sql.DB
	lastOnlineTimes *lastOnline.Times
	pushNotifier    push.Notifier
	spartaClient    *sparta.Client
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

) *restful.WebService {
	ctx := &context{
		broadcaster:     broadcaster,
		db:              db,
		lastOnlineTimes: lastOnlineTimes,
		pushNotifier:    pushNotifier,
		spartaClient:    spartaClient,
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
		Notes("This returns a list of convo ids that have been pinned by the user").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "List of pinned ids", IdList{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil))

	//
	// path: /users/{uid}/pinned/{cid}
	//

	ws.Route(ws.PUT("/{uid}/pinned/{cid}").Filter(filters.UserIsTarget).To(ctx.pinConvo).
		Doc("Pin a conversation").
		Notes("User must be a member of any convo they wish to pin").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Param(ws.PathParameter("cid", "Convo id of conversation to pin").DataType("string")).
		Returns(200, "Conversation pinned", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Not Found", nil))

	ws.Route(ws.DELETE("/{uid}/pinned/{cid}").Filter(filters.UserIsTarget).To(ctx.unpinConvo).
		Doc("Unpin a conversation").
		Notes("This request is idempotent and may return 200 even if the conversation was never originally pinned").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Param(ws.PathParameter("cid", "Convo id of conversation to unpin").DataType("string")).
		Returns(200, "Conversation not pinned", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil))

	//
	// path: /users/{uid}/typing/{cid}
	//

	ws.Route(ws.POST("/{uid}/typing/{cid}").Filter(filters.UserIsTarget).To(ctx.postTyping).
		Doc("Mark the user as \"typing\" in a conversation").
		Notes("User must be a member of any convo in which they are typing").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Param(ws.PathParameter("cid", "Convo id of conversation").DataType("string")).
		Returns(200, "Marked as \"typing\"", nil).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Not Found", nil))

	//
	// path: /users/{uid}/settings
	//

	ws.Route(ws.GET("/{uid}/settings").Filter(filters.UserIsTarget).To(ctx.getSettings).
		Doc("Get the user's settings map").
		Notes("These are persistent settings consistent across all the user's devices").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Returns(200, "Settings map", UserSettings{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Not Found", nil))

	ws.Route(ws.PUT("/{uid}/settings").Filter(filters.UserIsTarget).To(ctx.putSettings).
		Doc("Edit keys in the user's settings map").
		Notes("These are persistent settings consistent across all the user's devices").
		Param(ws.PathParameter("uid", "User id (email)").DataType("string")).
		Reads(UserSettings{}).
		Returns(200, "Returns new settings map", UserSettings{}).
		Returns(401, "Invalid authorization", nil).
		Returns(403, "Forbidden", nil).
		Returns(404, "Not Found", nil))

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
	defer tx.Rollback()

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
	defer tx.Rollback()

	var dbInsertOrUpdate func(*sql.Tx, *User) error
	userExists := dao.UserExists(tx, uid)
	if userExists {
		dbInsertOrUpdate = dao.UpdateUser
	} else {
		dbInsertOrUpdate = dao.InsertUser
	}

	err := dbInsertOrUpdate(tx, newUser)
	if errors.UniqueConstraintFailed(err) {
		if newUser.TagId == params.TagId {
			// tag collision with given tag id
			response.WriteErrorString(409, "User with tagId "+params.TagId+" already exists")
			return
		} else {
			// tag collision with default tag id; append a random 32-bit number
			newUser.TagId = newUser.TagId + fmt.Sprintf("%v", rand.Uint32())
			err = dbInsertOrUpdate(tx, newUser)
			errors.PanicAndRollbackOnErr(err, tx)
		}
	} else {
		errors.PanicAndRollbackOnErr(err, tx)
	}

	if userExists {
		err = ctx.spartaClient.UpdateUser(newUser)
	} else {
		err = ctx.spartaClient.CreateOrUpdateUser(newUser)
	}
	errors.PanicAndRollbackOnErr(err, tx)

	dao.CommitOrPanic(tx)

	response.WriteEntity(newUser)
	broadcast.SendUserEvent(ctx.broadcaster, uid)
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
	defer tx.Rollback()

	dao.UpdateAvatar(tx, uid, bytes)
	dao.CommitOrPanic(tx)

	broadcast.SendUserAvatarEvent(ctx.broadcaster, uid)
}

func (ctx *context) getPinned(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	cids := dao.GetPinned(tx, caller)
	dao.CommitOrPanic(tx)

	response.WriteEntity(IdList{Ids: cids})
}

func (ctx *context) pinConvo(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	if !dao.UserExists(tx, cid) && !dao.ConvoExists(tx, cid) {
		response.WriteHeader(404)
		return
	}
	dao.SetPinned(tx, caller, cid)
	dao.CommitOrPanic(tx)

	broadcast.SendPinEvent(ctx.broadcaster, caller, cid)
}

func (ctx *context) unpinConvo(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	dao.SetUnpinned(tx, caller, cid)
	dao.CommitOrPanic(tx)

	broadcast.SendUnpinEvent(ctx.broadcaster, caller, cid)
}

func (ctx *context) postTyping(request *restful.Request, response *restful.Response) {
	cid := request.PathParameter("cid")
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	convo := dao.GetConvo(tx, cid, caller)
	if convo == nil {
		response.WriteHeader(404)
		return
	} else if !convo.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}
	dao.CommitOrPanic(tx)

	broadcast.SendTypingEvent(ctx.broadcaster, caller, cid, convo.Members)
}

func (ctx *context) getSettings(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	settings := dao.GetSettings(tx, caller)
	if settings == nil {
		response.WriteHeader(404)
		return
	}

	dao.CommitOrPanic(tx)

	response.WriteEntity(settings)
}

func (ctx *context) putSettings(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)

	var params = new(UserSettings)
	err := request.ReadEntity(params)
	errors.PanicOnErr(err)

	// add checks for new settings here
	if params.NotifyOnlyOnTag == nil {
		response.WriteErrorString(400, "Must change at least one setting")
		return
	}

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	dao.ChangeSettings(tx, caller, params)
	settings := dao.GetSettings(tx, caller)
	if settings == nil {
		response.WriteHeader(404)
		return
	}

	dao.CommitOrPanic(tx)

	response.WriteEntity(settings)
	broadcast.SendSettingsEvent(ctx.broadcaster, caller)
}
