package lipwig

import (
	"aerofs.com/sloth/dao"
	"aerofs.com/sloth/errors"
	"aerofs.com/sloth/util/set"
	"database/sql"
	"log"
	"time"
)

func (h *eventHandler) syncAcls() {
	defer errors.RecoverAndLog()
	log.Println("fetching shared folders")

	shares, epoch, err := h.spartaClient.GetAllSharedFolders()
	if err != nil {
		log.Printf("error syncing acls: %v\n", err)
		return
	}

	// compile map of SID -> Set<UID> from Sparta
	newMembership := make(map[string]set.Set)
	for _, share := range shares {
		s := set.New()
		for _, user := range share.Members {
			s.Add(user.Email)
		}
		newMembership[share.Id] = s
	}

	tx := dao.BeginOrPanic(h.db)
	defer tx.Rollback()

	currentEpoch, ok := dao.GetAclEpoch(tx)
	if ok && currentEpoch >= epoch {
		log.Printf("ignoring stale epoch %v, current is %v\n", epoch, currentEpoch)
		return
	}
	log.Print("new acl epoch ", epoch)
	dao.SetAclEpoch(tx, epoch)

	// compile map of SID -> Set<UID> from DB
	oldMembership := dao.GetAllStoreMembership(tx)

	now := time.Now()
	for sid, oldUids := range oldMembership {
		newUids := newMembership[sid]
		removed := oldUids.Diff(newUids)
		added := newUids.Diff(oldUids)
		if len(added) == 0 && len(removed) == 0 {
			log.Print("no acl updates for ", sid)
			continue
		}
		cid := dao.GetCidForSid(tx, sid)
		if len(added) == 0 && len(removed) == len(oldUids) {
			log.Print("error: cannot remove all users from ", cid)
			continue
		}
		removeMembers(tx, cid, removed, now)
		addMembers(tx, cid, added, now)
	}

	dao.CommitOrPanic(tx)
}

func removeMembers(tx *sql.Tx, cid string, uids set.Set, now time.Time) {
	for uid := range uids {
		log.Print(cid, " remove ", uid)
		dao.RemoveMember(tx, cid, uid)
		dao.InsertMemberRemovedMessage(tx, cid, uid, "", now)
	}
}

func addMembers(tx *sql.Tx, cid string, uids set.Set, now time.Time) {
	for uid := range uids {
		log.Print(cid, " add ", uid)
		dao.InsertMember(tx, cid, uid)
		dao.InsertMemberAddedMessage(tx, cid, uid, "", now)
	}
}
