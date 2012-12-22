/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

public class SingleuserStoreJoiner implements IStoreJoiner
{
    private final static Logger l = Util.l(SingleuserStoreJoiner.class);

    private final SingleuserStores _stores;
    private final ObjectCreator _oc;
    private final ObjectDeleter _od;
    private final DirectoryService _ds;
    private final CfgRootSID _cfgRootSID;

    @Inject
    public SingleuserStoreJoiner(DirectoryService ds, SingleuserStores stores, ObjectCreator oc,
            ObjectDeleter od, CfgRootSID cfgRootSID)
    {
        _ds = ds;
        _oc = oc;
        _od = od;
        _stores = stores;
        _cfgRootSID = cfgRootSID;
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
        if (_ds.hasOA_(new SOID(root, oid))) return;

        assert folderName != null : sidx + " " + sid.toStringFormal();

        SOID anchor = new SOID(root, SID.storeSID2anchorOID(sid));
        OA oaAnchor = _ds.getOANullable_(anchor);

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
            return;
        }

        l.info("joining share: " + sidx + " " + folderName);

        while (true) {
            try {
                /**
                 * We do not increment version vector on purpose to avoid false conflicts
                 * between multiple devices of the same user joining the shared folder
                 *
                 * TODO: do we need to keep detectEmigration set to true?
                 */
                _oc.createMeta_(Type.ANCHOR, anchor, OID.ROOT, folderName, 0, PhysicalOp.APPLY,
                        true, false, t);
                break;
            } catch (ExAlreadyExist e) {
                folderName = Util.nextFileName(folderName);
            }
        }

        // TODO: send path of joined folder via Ritual Notification

        l.debug("joined " + sid + " at " + folderName);
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

        SOID anchor = new SOID(_stores.getParent_(sidx), SID.storeSID2anchorOID(sid));
        OA oa = _ds.getOA_(anchor);

        // nothing to do if the anchor is already deleted...
        // NB: isDeleted may be expensive and !expelled => !deleted
        if (oa.isExpelled() && _ds.isDeleted_(oa)) return;

        l.info("leaving share: " + sidx + " " + sid);
        _od.delete_(anchor, PhysicalOp.APPLY, null, t);

        // TODO: send path of folder left via Ritual notification?
    }
}
