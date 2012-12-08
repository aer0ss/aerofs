/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IStoreJoiner;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UserID;
import com.google.inject.Inject;

import java.util.Map;

public class SingleuserStoreJoiner implements IStoreJoiner
{
    private final SingleuserStores _stores;
    private final ObjectCreator _oc;
    private final DirectoryService _ds;
    private final CfgRootSID _cfgRootSID;

    @Inject
    public SingleuserStoreJoiner(DirectoryService ds, SingleuserStores stores, ObjectCreator oc,
            CfgRootSID cfgRootSID)
    {
        _ds = ds;
        _oc = oc;
        _stores = stores;
        _cfgRootSID = cfgRootSID;
    }

    @Override
    public void joinStore_(SIndex sidx, SID sid, String folderName, Map<UserID, Role> newRoles,
            Trans t) throws Exception
    {
        // ignore changes on the root store.
        if (sid.equals(_cfgRootSID.get())) return;

        SIndex parent = _stores.getRoot_();

        assert folderName != null : sidx + " " + sid.toStringFormal();

        SOID anchor = new SOID(parent, SID.storeSID2anchorOID(sid));
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

        Util.l(this).info("joining share: " + sidx + " " + folderName);

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

        Util.l(this).debug("joined " + sid + " at " + folderName);
    }

    @Override
    public void leaveStore_(SIndex sidx, SID sid, Map<UserID, Role> newRoles, Trans t) throws Exception
    {
        // we should never be asked to leave the root store.
        assert !sid.equals(_cfgRootSID.get());

        // TODO:
    }
}
