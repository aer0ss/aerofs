/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.acl.SharedFolderAutoLeaver;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.notification.RitualNotificationServer;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Collection;

public class SingleuserStoreJoiner implements IStoreJoiner
{
    private final static Logger l = Loggers.getLogger(SingleuserStoreJoiner.class);

    private final SingleuserStores _stores;
    private final ObjectCreator _oc;
    private final ObjectDeleter _od;
    private final DirectoryService _ds;
    private final CfgRootSID _cfgRootSID;
    private final RitualNotificationServer _rns;
    private final SharedFolderAutoLeaver _lod;
    private final IMetaDatabase _mdb;

    @Inject
    public SingleuserStoreJoiner(DirectoryService ds, SingleuserStores stores, ObjectCreator oc,
            ObjectDeleter od, CfgRootSID cfgRootSID, RitualNotificationServer rns,
            SharedFolderAutoLeaver lod, IMetaDatabase mdb)
    {
        _ds = ds;
        _oc = oc;
        _od = od;
        _stores = stores;
        _cfgRootSID = cfgRootSID;
        _rns = rns;
        _lod = lod;
        _mdb = mdb;
    }

    @Override
    public void joinStore_(SIndex sidx, SID sid, String folderName, Trans t) throws Exception
    {
        // ignore changes on the root store.
        if (sid.equals(_cfgRootSID.get())) return;

        SIndex root = _stores.getRoot_();

        /**
         * If the original folder is already present in the root store we should not auto-join but
         * instead wait for the conversion to propagate. We do not check for its presence in other
         * stores as some migration scenarios could lead us to wrongly ignore explicit joins (e.g
         * if a subfolder of a shared folder is moved up to the root store, shared and joined
         * before the original move propagates)
         */
        OID oid = SID.convertedStoreSID2folderOID(sid);
        OA oaFolder = _ds.getOANullable_(new SOID(root, oid));
        if (oaFolder != null && !_ds.isDeleted_(oaFolder)) {
            l.info("original folder already present");
            return;
        }

        assert folderName != null : sidx + " " + sid.toStringFormal();

        SOID anchor = new SOID(root, SID.storeSID2anchorOID(sid));
        OA oaAnchor = _ds.getOANullable_(anchor);

        /**
         * By default, we do not increment version vector on purpose to avoid false conflicts
         * between multiple devices of the same user joining the shared folder
         */
        boolean updateVersion = false;

        /*
         * If we receive the ACL update after HdShareFolder successfully migrates the folder,
         * ACLSynchronizer will detect we were granted access to a new store and call this method
         * but we don't need to do anything as HdShareFolder already created the new store.
         *
         * Similarly, if we get an anchor creation message from the sharer and successfully dl the
         * anchor before receiving the ACL update we don't have anything to do.
         *
         * NB: we could conceivably move this check to ACLSynchronizer but that would make it
         * dependent on DirectoryService which is probably a bad idea...
         */
        if (oaAnchor != null) {
            assert oaAnchor.isAnchor() : anchor;
            if (_ds.isDeleted_(oaAnchor)) {
                l.info("restore deleted anchor");
                /**
                 * If the user was kicked out and later re-invited the anchor will be present in its
                 * trash. Moving the anchor out of the trash would be too cumbersome so we simply
                 * delete the existing OA and proceed to re-create it as if it had never existed
                 */
                _ds.deleteOA_(anchor, t);

                /**
                 * when restoring a deleted anchor we are forced to update the version
                 */
                updateVersion = true;
            } else {
                l.info("anchor already present");
                return;
            }
        }

        l.info("joining share: " + sidx + " " + folderName);

        // make sure we don't have an old "leave request" queued
        _lod.removeFromQueue_(sid, t);

        while (true) {
            try {
                _oc.createMeta_(Type.ANCHOR, anchor, OID.ROOT, folderName, 0, PhysicalOp.APPLY,
                        false, updateVersion, t);
                break;
            } catch (ExAlreadyExist e) {
                folderName = Util.nextFileName(folderName);
            }
        }

        l.debug("joined " + sid + " at " + folderName);

        _rns.sendEvent_(PBNotification.newBuilder()
                .setType(PBNotification.Type.SHARED_FOLDER_JOIN)
                .setPath(Path.fromString(folderName).toPB())
                .build());
    }

    @Override
    public void leaveStore_(SIndex sidx, SID sid, Trans t) throws Exception
    {
        /**
         * Because there is currently no ACL stored for root stores, we will be asked to leave the
         * root store on every ACL update. Simply ignore that for the time being. When we do store
         * ACL for root stores (for team server's benefit) we should switch to an assert...
         */
        if (sid.equals(_cfgRootSID.get())) return;

        l.info("leaving share: " + sidx + " " + sid);

        OID oid = SID.storeSID2anchorOID(sid);

        // we abuse the db query designed for AdmittedObjectLocator
        // NB: we cannot rely on parent relationships for the sidx as the anchor might be expelled
        // NB: we cannot use AdmittedObjectLocator as any explicitly expelled (but non-deleted)
        // anchor needs to be deleted and AOL would simply ignore them...
        Collection<SIndex> sidxs = _mdb.getSIndexes_(oid, new SIndex(-1));

        for (SIndex sidxWithanchor : sidxs) {
            SOID soid = new SOID(sidxWithanchor, oid);
            OA oa = _ds.getOA_(soid);
            assert oa.soid().oid().equals(oid);
            assert oa.type() == Type.ANCHOR;
            deleteAnchorIfNeeded_(oa, t);
        }
    }

    private void deleteAnchorIfNeeded_(OA oa, Trans t) throws Exception
    {
        // nothing to do if the anchor is already deleted...
        // NB: isDeleted may be expensive and !expelled => !deleted
        if (oa.isExpelled() && _ds.isDeleted_(oa)) return;

        Path path = _ds.resolve_(oa);
        _od.delete_(oa.soid(), PhysicalOp.APPLY, t);

        _rns.sendEvent_(PBNotification.newBuilder()
                .setType(PBNotification.Type.SHARED_FOLDER_KICKOUT)
                .setPath(path.toPB())
                .build());
    }
}
