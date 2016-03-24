package convos

import (
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/filters"
	. "aerofs.com/sloth/structs"
	"aerofs.com/sloth/util/set"
	"github.com/emicklei/go-restful"
	"log"
)

func (ctx *context) updateConvo(request *restful.Request, response *restful.Response) {
	caller := request.Attribute(filters.AUTHORIZED_USER).(string)
	cid := request.PathParameter("cid")

	tx := dao.BeginOrPanic(ctx.db)
	defer tx.Rollback()

	c := dao.GetConvo(tx, cid, caller)
	if c == nil {
		response.WriteHeader(404)
		return
	}
	if !c.IsPublic && !c.HasMember(caller) {
		response.WriteErrorString(403, "Forbidden")
		return
	}
	if c.Type == "DIRECT" {
		response.WriteErrorString(400, "Cannot modify a direct convo")
		return
	}

	p := new(GroupConvoWritable)
	err := request.ReadEntity(p)
	errors.PanicAndRollbackOnErr(err, tx)

	if p.IsPublic != nil && *p.IsPublic && !c.IsPublic {
		response.WriteErrorString(400, "Cannot make a private convo public")
		return
	}

	updated := dao.UpdateConvo(tx, c, p, caller)
	dao.CommitOrPanic(tx)

	if c.Sid != "" {
		go func() {
			oldMembers := set.From(c.Members)
			newMembers := set.From(p.Members)
			added := newMembers.Diff(oldMembers)
			removed := oldMembers.Diff(newMembers)
			for uid := range added {
				err := ctx.spartaClient.AddSharedFolderMember(c.Sid, uid)
				if err != nil {
					log.Printf("err adding %v to %v: %v\n", uid, c.Sid, err)
				}
			}
			for uid := range removed {
				err := ctx.spartaClient.RemoveSharedFolderMember(c.Sid, uid)
				if err != nil {
					log.Printf("err removing %v from %v: %v\n", uid, c.Sid, err)
				}
			}
		}()
	}

	response.WriteEntity(updated)

	broadcast.SendConvoEvent(ctx.broadcaster, c.Id, updated.Members)
	broadcast.SendMessageEvent(ctx.broadcaster, c.Id, updated.Members)
}
