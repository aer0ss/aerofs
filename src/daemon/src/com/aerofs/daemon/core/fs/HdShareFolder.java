package com.aerofs.daemon.core.fs;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import static com.aerofs.daemon.core.ds.OA.Type.ANCHOR;
import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import static com.aerofs.daemon.core.phy.PhysicalOp.NOP;

import com.aerofs.daemon.core.store.DescendantStores;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.acl.SubjectRolePairs;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.ex.ExExpelled;
import com.aerofs.lib.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.UserID;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.google.inject.Inject;

public class HdShareFolder extends AbstractHdIMC<EIShareFolder>
{
    private final LocalACL _lacl;
    private final TC _tc;
    private final TransManager _tm;
    private final ObjectCreator _oc;
    private final DirectoryService _ds;
    private final IImmigrantCreator _imc;
    private final ObjectMover _om;
    private final ObjectDeleter _od;
    private final IMapSID2SIndex _sid2sidx;
    private final IStores _ss;
    private final DescendantStores _dss;

    @Inject
    public HdShareFolder(LocalACL lacl, TC tc, TransManager tm, ObjectCreator oc, DirectoryService ds,
            IImmigrantCreator imc, ObjectMover om, ObjectDeleter od, IMapSID2SIndex sid2sidx,
            IStores ss, DescendantStores dss)
    {
        _ss = ss;
        _lacl = lacl;
        _tc = tc;
        _tm = tm;
        _oc = oc;
        _ds = ds;
        _imc = imc;
        _om = om;
        _od = od;
        _sid2sidx = sid2sidx;
        _dss = dss;
    }

    @Override
    protected void handleThrows_(EIShareFolder ev, Prio prio) throws Exception
    {
        OA oa = checkSanity_(ev._path);

        checkACL_(ev.user(), ev._path);

        // calculate the SID
        boolean alreadyShared = false;
        SID sid;
        if (oa.isAnchor()) {
            alreadyShared = true;
            sid = SID.anchorOID2storeSID(oa.soid().oid());
        } else if (oa.isDir()) {
            sid = SID.folderOID2convertedStoreSID(oa.soid().oid());
        } else {
            throw new ExNotDir();
        }


        //
        // IMPORTANT: the order of operations in the following code matters
        //
        // You have to:
        // 1) contact the central ACL server to share the folder
        // 2) convert the local folder into a shared folder
        //
        // We contact the remote _first_ to update the acls because remote calls are much more
        // likely to fail than local ones. A failure will safely prevent the folder from being
        // converted into a store (i.e. a half-state in the system).
        //
        // If the order were reversed we would convert the store locally first,
        // and then update the acls for this store. At the same time,
        // the migration process has started, and objects will be deleted from the old folder and
        // added to the store. At the same time, this update would be propagated to other devices
        // for the user. If the RPC call fails however, neither the sharer or their devices would
        // have permissions to receive the files or make modifications to the store,
        // resulting in all the contents vanishing. Not good. Moreover,
        // the system is in a half-state; the folder has been shared,
        // but no acls for the owner exist; again, not good.
        //
        // To prevent this we contact the remote first, verify that the acls are added, and then,
        // make changes locally. Since the acl update process is idempotent from the perspective
        // of the stores' owner, multiple local failures can be handled properly,
        // while remote failures will prevent the system from being in a half-state
        //

        shareFolderThroughSP_(ev._subject2role, ev._emailNote, ev.user(), ev._path.last(), sid,
                alreadyShared);

        if (!alreadyShared) convertToSharedFolder_(ev._path, oa, sid);

        ev.setResult_(sid);
    }

    private void shareFolderThroughSP_(Map<UserID, Role> subject2role, String emailNote,
            UserID user, String folderName, SID sid, boolean alreadyShared) throws Exception
    {
        // always add the user as the owner
        if (!alreadyShared) {
            subject2role = new TreeMap<UserID, Role>(subject2role);
            subject2role.put(user, Role.OWNER);
        }

        if (!subject2role.isEmpty()) {
            callSP_(sid, folderName, SubjectRolePairs.mapToPB(subject2role), emailNote);
        }
    }

    private OA checkSanity_(Path path)
            throws SQLException, ExNotFound, ExNoPerm, ExExpelled, ExParentAlreadyShared,
            ExChildAlreadyShared
    {
        SOID soid = _ds.resolveThrows_(path);
        if (soid.oid().isRoot() || soid.oid().isTrash()) {
            throw new ExNoPerm("can't share system folders");
        }

        // throw if the object is expelled
        OA oa = _ds.getOA_(soid);
        if (oa.isExpelled()) throw new ExExpelled();

        // throw if a parent folder is already shared
        if (!_ss.isRoot_(soid.sidx())) throw new ExParentAlreadyShared();

        Set<SIndex> descendants = _dss.getDescendantStores_(soid);
        if (!descendants.isEmpty()) throw new ExChildAlreadyShared();
        return oa;
    }

    private void checkACL_(UserID user, Path path)
            throws ExNotFound, SQLException, ExNoPerm, ExExpelled
    {
        assert !path.isEmpty(); // guaranteed by the above check
        Path pathParent = path.removeLast();
        _lacl.checkThrows_(user, pathParent, Role.OWNER);
    }

    private void convertToSharedFolder_(Path path, OA oa, SID sid)
            throws Exception
    {
        assert oa.isDir();

        Trans t = _tm.begin_();
        try {
            converttoSharedFolderImpl_(oa.soid(), oa.parent(), sid, path, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Pseudo-pause and make a call to SP to share the folder
     */
    private void callSP_(SID sid, String folderName, List<PBSubjectRolePair> roles,
            String emailNote) throws Exception
    {
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "spacl");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("spacl");
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            sp.shareFolder(folderName, sid.toPB(), roles, emailNote);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }
    }

    /**
     * Convert an existing folder into a store.
     */
    private void converttoSharedFolderImpl_(SOID soid, OID oidParent, SID sid, Path path, Trans t)
            throws Exception
    {
        // Step 1: rename the folder into a temporary name, without incrementing its version.
        assert !path.isEmpty(); // the caller guarantees this
        Path pathTemp = path;
        do {
            pathTemp = path.removeLast().append(Util.nextFileName(pathTemp.last()));
        } while (_ds.resolveNullable_(pathTemp) != null);
        _om.moveInSameStore_(soid, oidParent, pathTemp.last(), NOP, false, false, t);

        // Step 2: create the new store with a derived SID
        SOID soidAnchor = new SOID(soid.sidx(), SID.storeSID2anchorOID(sid));
        _oc.createMeta_(ANCHOR, soidAnchor, oidParent, path.last(), 0, MAP, true, true, t);

        // Step 3: migrate files
        SIndex sidxTo = _sid2sidx.get_(sid);
        SOID soidToRoot = new SOID(sidxTo, OID.ROOT);
        for (OID oidChild : _ds.getChildren_(soid)) {
            SOID soidChild = new SOID(soid.sidx(), oidChild);
            OA oaChild = _ds.getOA_(soidChild);
            _imc.createImmigrantRecursively_(soidChild, soidToRoot, oaChild.name(), MAP, t);
        }

        // Step 4: delete the root folder
        _od.delete_(soid, NOP, sid, t);
    }
}
