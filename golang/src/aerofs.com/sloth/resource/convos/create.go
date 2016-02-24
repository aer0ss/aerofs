package convos

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	. "aerofs.com/sloth/structs"
	"github.com/emicklei/go-restful"
	"log"
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
		// if the "sid" field is not empty, this signals a request for a new
		// shared folder
		if p.Sid != "" {
			p.Sid, err = ctx.spartaClient.CreateSharedFolder(p.Members[0], *p.Name)
			errors.PanicOnErr(err)
			log.Print("created share with id ", p.Sid)
			// FIXME: sequential network calls are bad, and I feel bad
			for _, uid := range p.Members {
				log.Print("adding ", uid, " to ", p.Sid)
				err := ctx.spartaClient.AddSharedFolderMember(p.Sid, uid)
				errors.PanicOnErr(err)
			}
			ctx.lipwigClient.SubscribeAndHandlePolaris(p.Sid)
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
		if len(p.Members) < 2 {
			response.WriteErrorString(400, "Request body must list at least two members")
			return
		}
		if !contains(p.Members, caller) {
			response.WriteErrorString(400, "Members list must include caller")
			return
		}

		tx := dao.BeginOrPanic(ctx.db)
		defer tx.Rollback()
		convo = dao.GetDirectConvo(tx, p.Members, caller)
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

func contains(xs []string, x string) bool {
	for _, p := range xs {
		if p == x {
			return true
		}
	}
	return false
}
