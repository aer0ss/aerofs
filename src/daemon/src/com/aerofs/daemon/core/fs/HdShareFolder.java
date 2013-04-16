package com.aerofs.daemon.core.fs;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import static com.aerofs.daemon.core.ds.OA.Type.ANCHOR;
import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;

import static com.aerofs.daemon.core.phy.PhysicalOp.APPLY;
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
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.acl.Role;
import com.aerofs.base.acl.SubjectRolePairs;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.id.OID;
import com.aerofs.lib.Path;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.UserID;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.proto.Common.PBSubjectRolePair;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class HdShareFolder extends AbstractHdIMC<EIShareFolder>
{
    private final static Logger l = Loggers.getLogger(HdShareFolder.class);

    private final ACLChecker _acl;
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
    private final ACLSynchronizer _aclsync;
    private final SPBlockingClient.Factory _factSP;
    private final CfgLocalUser _localUser;
    private final CfgAbsRoots _absRoots;

    @Inject
    public HdShareFolder(ACLChecker acl, TC tc, TransManager tm, ObjectCreator oc,
            DirectoryService ds, IImmigrantCreator imc, ObjectMover om, ObjectDeleter od,
            IMapSID2SIndex sid2sidx, IStores ss, DescendantStores dss, ACLSynchronizer aclsync,
            SPBlockingClient.Factory factSP, CfgLocalUser localUser, CfgAbsRoots cfgAbsRoots)
    {
        _ss = ss;
        _acl = acl;
        _tc = tc;
        _tm = tm;
        _oc = oc;
        _ds = ds;
        _imc = imc;
        _om = om;
        _od = od;
        _sid2sidx = sid2sidx;
        _dss = dss;
        _aclsync = aclsync;
        _factSP = factSP;
        _localUser = localUser;
        _absRoots = cfgAbsRoots;
    }

    @Override
    protected void handleThrows_(EIShareFolder ev, Prio prio) throws Exception
    {
        l.info("sharing: " + ev._path);

        OA oa;
        SID sid;
        boolean alreadyShared = false;

        if (ev._path.sid().isUserRoot()) {
            // physical root is a user root store: the root itself cannot be shared but one level of
            // nested sharing is allowed (i.e folders under the root can be shared)
            oa = checkSanity_(ev._path);
            checkACL_(ev.user(), ev._path);

            if (oa.isAnchor()) {
                alreadyShared = true;
                sid = SID.anchorOID2storeSID(oa.soid().oid());
            } else if (oa.isDir()) {
                sid = SID.folderOID2convertedStoreSID(oa.soid().oid());
            } else {
                throw new ExNotDir();
            }
        } else {
            // physical root is an external shared folder: the root can be shared an no nested
            // sharing is allowed as it would break consistency (an external shared folder for
            // one user may be located under the root anchor for another)
            if (!ev._path.isEmpty()) throw new ExParentAlreadyShared();
            alreadyShared = true;
            sid = ev._path.sid();
            oa = null;
            // check that the sid is a valid external root
            if (_absRoots.get(sid) == null) throw new ExBadArgs();
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

        String name = Util.sharedFolderName(ev._path, _absRoots);
        callSP_(sid, name, SubjectRolePairs.mapToPB(ev._subject2role), ev._emailNote);

        if (!alreadyShared) convertToSharedFolder_(ev._path, oa, sid);

        // ensure ACLs are updated (at the very least we need an entry for the local user...)
        _aclsync.syncToLocal_();

        l.info("shared: " + ev._path + " -> " + sid.toStringFormal());
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
        _acl.checkThrows_(user, pathParent, Role.OWNER);
    }

    /**
     * Pseudo-pause and make a call to SP to share the folder
     */
    private void callSP_(SID sid, String folderName, List<PBSubjectRolePair> roles,
            String emailNote) throws Exception
    {
        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "sp-share");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("sp-share");
            SPBlockingClient sp = _factSP.create_(_localUser.get());
            sp.signInRemote();
            // external shared folders are create by HdLinkRoot only
            sp.shareFolder(folderName, sid.toPB(), roles, emailNote, false);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }
    }

    private void convertToSharedFolder_(Path path, OA oa, SID sid)
            throws Exception
    {
        assert oa.isDir();

        SOID soid = oa.soid();

        cleanupAnchorCreatedByAutoJoin_(soid.sidx(), sid);

        Trans t = _tm.begin_();
        try {
            convertToSharedFolderImpl_(soid, oa.parent(), sid, path, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /*
     * As we release the core lock when making the SP call to join the shared folder it is
     * possible that the new ACLs propagate before we regain the core lock. In this case
     * we end up with an improperly named anchor associated with a superfluous physical
     * directory. We need to fix this before we can proceed with the conversion.
     *
     * Due to some assertion enforced by VersionAssistant the deletion of this object may
     * not take place in the transaction that does the actual migration.
     */
    private void cleanupAnchorCreatedByAutoJoin_(SIndex parentStore, SID sid) throws Exception
    {
        SOID anchor = new SOID(parentStore, SID.storeSID2anchorOID(sid));
        OA oaAnchor = _ds.getOANullable_(anchor);
        if (oaAnchor == null) return;

        l.debug("cleanup auto-join " + sid + " " + oaAnchor.name());

        Trans t = _tm.begin_();
        try {
            _od.delete_(anchor, APPLY, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Convert an existing folder into a store.
     */
    private void convertToSharedFolderImpl_(SOID soid, OID oidParent, SID sid, Path path, Trans t)
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
        createNewStore(soid, oidParent, sid, path, t);

        // Step 3: migrate files
        SIndex sidxTo = _sid2sidx.get_(sid);
        SOID soidToRoot = new SOID(sidxTo, OID.ROOT);
        for (OID oidChild : _ds.getChildren_(soid)) {
            SOID soidChild = new SOID(soid.sidx(), oidChild);
            OA oaChild = _ds.getOA_(soidChild);
            _imc.createImmigrantRecursively_(soidChild, soidToRoot, oaChild.name(), MAP, t);
        }

        // Step 4: delete the root folder
        _od.deleteAndEmigrate_(soid, NOP, sid, t);
    }

    private void createNewStore(SOID soid, OID oidParent, SID sid, Path path, final Trans t)
            throws Exception
    {
        l.debug("new store: " + sid + " " + path);
        SOID soidAnchor = new SOID(soid.sidx(), SID.storeSID2anchorOID(sid));

        OA oaAnchor = _ds.getOANullable_(soidAnchor);
        if (oaAnchor != null) {
            // any conflicting anchor created by auto-join upon ACL update should have been cleaned
            // before starting the conversion process
            assert oaAnchor.isExpelled() : oaAnchor;
            // move anchor to appropriate path (does not affect physical objects)
            _om.moveInSameStore_(oaAnchor.soid(), oidParent, path.last(), MAP, false, true, t);
        } else {
            // create anchor, root and trash, ...
            _oc.createMeta_(ANCHOR, soidAnchor, oidParent, path.last(), 0, MAP, true, true, t);
        }
    }
}
