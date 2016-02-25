package convos

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	. "aerofs.com/sloth/structs"
	"github.com/emicklei/go-restful"
)

const CHANNEL = 1
const DIRECT = 2

type baseRequest struct {
	Type string
}

func (ctx *context) createConvo(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	r := new(baseRequest)
	err := request.ReadEntity(r)
	errors.PanicOnErr(err)

	var convo *Convo
	switch r.Type {
	case "CHANNEL":
		p := new(GroupConvoWritable)
		err := request.ReadEntity(p)
		errors.PanicOnErr(err)
		if p.Name == nil || p.Members == nil || p.IsPublic == nil {
			response.WriteErrorString(400, "Request body must have \"name\", \"is_public\", and \"members\" keys")
			return
		}
		tx := dao.BeginOrPanic(ctx.db)
		convo = dao.CreateGroupConvo(tx, p, caller)
		dao.CommitOrPanic(tx)

	case "DIRECT":
		p := new(DirectConvoWritable)
		err := request.ReadEntity(p)
		errors.PanicOnErr(err)
		if p.Members == nil {
			response.WriteErrorString(400, "Request body must have a \"members\" key")
			return
		}
		if len(p.Members) != 2 {
			response.WriteErrorString(400, "Request body must list exactly two members")
			return
		}
		var other string
		if caller == p.Members[0] {
			other = p.Members[1]
		} else if caller == p.Members[1] {
			other = p.Members[1]
		} else {
			response.WriteErrorString(400, "Caller must be a member of new direct convo")
			return
		}

		tx := dao.BeginOrPanic(ctx.db)
		defer tx.Rollback()
		convo = dao.GetDirectConvo(tx, caller, other)
		if convo == nil {
			convo = dao.CreateDirectConvo(tx, p)
		}
		dao.CommitOrPanic(tx)

	default:
		response.WriteErrorString(400, "Invalid convo type")
		return
	}

	response.WriteEntity(convo)
	broadcast.SendConvoEvent(ctx.broadcaster, convo.Id, convo.Members)
	broadcast.SendMessageEvent(ctx.broadcaster, convo.Id, convo.Members)
}
