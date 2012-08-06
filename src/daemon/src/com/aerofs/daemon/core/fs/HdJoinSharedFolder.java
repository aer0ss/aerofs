package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.acl.ExConcurrentACLUpdate;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Role;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.Path;
import com.google.inject.Inject;

public class HdJoinSharedFolder extends AbstractHdIMC<EIJoinSharedFolder>
{
    private static final Logger l = Util.l(HdJoinSharedFolder.class);

    private final LocalACL _lacl;
    private final ACLSynchronizer _aclsync;
    private final TransManager _tm;
    private final ObjectCreator _oc;
    private final ObjectMover _om;
    private final DirectoryService _ds;

    @Inject
    public HdJoinSharedFolder(LocalACL lacl, ACLSynchronizer aclsync, TransManager tm,
            ObjectCreator oc, ObjectMover om, DirectoryService ds)
    {
        _lacl = lacl;
        _tm = tm;
        _oc = oc;
        _aclsync = aclsync;
        _om = om;
        _ds = ds;
    }

    @Override
    protected void handleThrows_(EIJoinSharedFolder ev, Prio prio) throws Exception
    {
        assert !ev._path.isEmpty();

        Path pathParent = ev._path.removeLast();
        SOID soidParent = _lacl.checkThrows_(ev.user(), pathParent, Role.EDITOR);

        // keep ACL up to date so we can start syncing instantly after joining the store.
        try {
            _aclsync.syncToLocal_();
        } catch (ExConcurrentACLUpdate e) {
            l.warn("concurrent ACL update. ignore");
        }

        Trans t = _tm.begin_();
        try {
            l.info("join " + ev._path + " sid " + ev._sid);

            SOID soidAnchor = new SOID(soidParent.sidx(), SID.storeSID2anchorOID(ev._sid));

            String name = ev._path.last();
            if (_ds.hasOA_(soidAnchor)) {
                _om.moveInSameStore_(soidAnchor, soidParent.oid(), name, PhysicalOp.APPLY, false,
                        true, t);
            } else {
                _oc.createMeta_(OA.Type.ANCHOR, soidAnchor, soidParent.oid(), name, 0,
                        PhysicalOp.APPLY, true, true, t);
            }
            t.commit_();
        } finally {
            t.end_();
        }
    }
}
