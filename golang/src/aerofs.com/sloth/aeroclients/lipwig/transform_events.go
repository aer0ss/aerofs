package lipwig

import (
	"aerofs.com/sloth/aeroclients/polaris"
	"aerofs.com/sloth/broadcast"
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/util"
	"aerofs.com/sloth/util/asynccache"
	"log"
)

func (h *eventHandler) syncTransformsLoop() {
	for {
		sid := <-h.sidsToSync
		h.syncTransforms(sid)
	}
}

func (h *eventHandler) syncTransforms(sid string) {
	defer errors.RecoverAndLog()
	transforms := h.fetchNewTransforms(sid)
	if len(transforms) == 0 {
		return
	}

	tx := dao.BeginOrPanic(h.db)
	defer tx.Rollback()
	oldLogicalTimestamp := dao.GetLastLogicalTimestamp(tx, sid)
	for _, t := range transforms {
		if t.LogicalTimestamp <= oldLogicalTimestamp {
			// already created a message for this transform
			log.Printf("skipping file update %v for sid %v\n", t.LogicalTimestamp, sid)
			continue
		}
		log.Printf("insert file update %v for sid %v\n", t.LogicalTimestamp, sid)
		fileCid := util.GenerateFileConvoId(t.Store + t.Oid)
		convo := dao.GetConvo(tx, fileCid, t.Uid)
		if convo != nil {
			dao.InsertFileUpdateMessage(tx, fileCid, t.Uid, t.Raw)
			broadcast.SendMessageEvent(h.broadcaster, convo.Id, convo.Members)
		}
	}
	lastLogicalTimestamp := transforms[len(transforms)-1].LogicalTimestamp
	dao.SetLastLogicalTimestamp(tx, sid, lastLogicalTimestamp)
	dao.CommitOrPanic(tx)
}

func (h *eventHandler) fetchNewTransforms(sid string) []*polaris.Transform {
	log.Print("fetch transforms for ", sid)

	// Fetch timestamp in its own transaction so that it doesn't span a network
	// round-trip. Two concurrent callers will not write the same message to
	// the db; at worst, we do some extra work, but correctness is preserved.
	tx := dao.BeginOrPanic(h.db)
	since := dao.GetLastLogicalTimestamp(tx, sid)
	dao.CommitOrPanic(tx)

	transforms := h.polarisClient.GetTransforms(sid, since)
	h.populateTransformUids(transforms)
	return transforms
}

func (h *eventHandler) populateTransformUids(transforms []*polaris.Transform) {
	// launch requests for dids and gather result channels
	// see the "util/asynccache" package for details
	results := make(map[string]<-chan asynccache.Result)
	for _, t := range transforms {
		did := t.Originator
		if _, ok := results[did]; !ok {
			results[did] = h.didOwners.Get(t.Originator)
		}
	}

	// wait for the results to complete and populate a did -> uid map
	uids := make(map[string]string)
	for did, c := range results {
		r := <-c
		errors.PanicOnErr(r.Error)
		uids[did] = r.Val
	}

	// populate transform structs' Uid field
	for _, t := range transforms {
		t.Uid = uids[t.Originator]
	}
}
