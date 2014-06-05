package com.aerofs.daemon.core.fs;

import com.aerofs.base.Loggers;
import com.aerofs.base.acl.SubjectPermissionsList;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.ex.ExExpelled;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.store.DescendantStores;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.fs.EIShareFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.PendingRootDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExChildAlreadyShared;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExParentAlreadyShared;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Common.PBSubjectPermissions;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.common.base.Objects;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static com.aerofs.daemon.core.ds.OA.Type.ANCHOR;
import static com.aerofs.daemon.core.phy.PhysicalOp.APPLY;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import static com.aerofs.daemon.core.phy.PhysicalOp.NOP;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class HdShareFolder extends AbstractHdIMC<EIShareFolder>
{
    private final static Logger l = Loggers.getLogger(HdShareFolder.class);

    private final TokenManager _tokenManager;
    private final TransManager _tm;
    private final ObjectCreator _oc;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final ImmigrantCreator _imc;
    private final ObjectMover _om;
    private final ObjectDeleter _od;
    private final IMapSID2SIndex _sid2sidx;
    private final IStores _ss;
    private final DescendantStores _dss;
    private final ACLSynchronizer _aclsync;
    private final SPBlockingClient.Factory _factSP;
    private final PendingRootDatabase _prdb;
    private final CfgAbsRoots _absRoots;

    @Inject
    public HdShareFolder(TokenManager tokenManager, TransManager tm, ObjectCreator oc,
            IPhysicalStorage ps, DirectoryService ds, ImmigrantCreator imc, ObjectMover om,
            ObjectDeleter od, IMapSID2SIndex sid2sidx, IStores ss, DescendantStores dss,
            ACLSynchronizer aclsync, InjectableSPBlockingClientFactory factSP,
            CfgAbsRoots cfgAbsRoots, PendingRootDatabase prdb)
    {
        _ss = ss;
        _tokenManager = tokenManager;
        _tm = tm;
        _oc = oc;
        _ds = ds;
        _ps = ps;
        _imc = imc;
        _om = om;
        _od = od;
        _sid2sidx = sid2sidx;
        _dss = dss;
        _aclsync = aclsync;
        _factSP = factSP;
        _absRoots = cfgAbsRoots;
        _prdb = prdb;
    }

    @Override
    protected void handleThrows_(EIShareFolder ev, Prio prio) throws Exception
    {
        l.info("sharing: {}", ev._path);

        OA oa;
        SID sid;
        String name;
        boolean alreadyShared = false;

        if (ev._path.sid().isUserRoot()) {
            // physical root is the user's default root store: it cannot be shared but its subfolders can.
            oa = throwIfUnshareable_(ev._path);

            if (oa.isAnchor()) {
                alreadyShared = true;
                sid = SID.anchorOID2storeSID(oa.soid().oid());
            } else if (oa.isDir()) {
                sid = SID.folderOID2convertedStoreSID(oa.soid().oid());
            } else {
                throw new ExNotDir();
            }
            name = sharedFolderName(ev._path, _absRoots);
        } else {
            // option 1: Block Storage
            // option 2: physical root is an external shared folder. The root can be shared and no
            // nested sharing is allowed as it would break consistency (an externally shared folder
            // for one user may be located under the root anchor for another)
            if (!ev._path.isEmpty()) throw new ExParentAlreadyShared();
            alreadyShared = true;
            sid = ev._path.sid();
            // reject unknown SID (failure to do so would lead to crash when determining the name)
            if (_sid2sidx.getLocalOrAbsentNullable_(sid) == null) throw new ExBadArgs();
            oa = null;
            // Grabing name for external shared folders. If the store is pending, query pending
            // root db else get it through the sharedFolderName util.
            name = _prdb.getPendingRoot(sid);
            if (name == null) {
                name = sharedFolderName(ev._path, _absRoots);
            }
        }

        //
        // IMPORTANT: the order of operations in the following code matters
        //
        // You have to:
        // 1) contact the central ACL server to share the folder
        // 2) convert the local folder into a shared folder
        //
        // We contact the remote _first_ to update the ACLs because remote calls are much more
        // likely to fail than local ones. A failure will safely prevent the folder from being
        // converted into a store (i.e. a half-state in the system).
        //
        // If the order were reversed we would convert the store locally first,
        // and then update the ACLs for this store. At the same time,
        // the migration process has started, and objects will be deleted from the old folder and
        // added to the store. At the same time, this update would be propagated to other devices
        // for the user. If the RPC call fails however, neither the sharer or their devices would
        // have permissions to receive the files or make modifications to the store,
        // resulting in all the contents vanishing. Not good. Moreover,
        // the system is in a half-state; the folder has been shared,
        // but no ACLs for the owner exist; again, not good.
        //
        // To prevent this we contact the remote first, verify that the ACLs are added, and then,
        // make changes locally. Since the acl update process is idempotent from the perspective
        // of the stores' owner, multiple local failures can be handled properly,
        // while remote failures will prevent the system from being in a half-state
        //
        callSP_(sid, name, SubjectPermissionsList.mapToPB(ev._subject2role), ev._emailNote,
                ev._suppressSharedFolderRulesWarnings);

        if (!alreadyShared) convertToSharedFolder_(ev._path, oa, sid);

        // ensure ACLs are updated (at the very least we need an entry for the local user...)
        _aclsync.syncToLocal_();

        l.info("shared: {} -> {}", ev._path, sid.toStringFormal());
    }

    /**
     * Derive the name of a shared folder from its Path
     * This is necessary to handle external roots, whose Path are empty and whose name are derived
     * from the physical folder they are linked too.
     */
    private static String sharedFolderName(Path path, CfgAbsRoots absRoots) throws SQLException
    {
        return path.isEmpty() ? new File(absRoots.get(path.sid())).getName() : path.last();
    }


    private OA throwIfUnshareable_(Path path)
            throws SQLException, ExNotFound, ExNoPerm, ExExpelled, ExParentAlreadyShared,
            ExChildAlreadyShared
    {
        SOID soid = _ds.resolveThrows_(path);

        // can't share root folder or trash
        if (soid.oid().isRoot() || soid.oid().isTrash()) {
            throw new ExNoPerm("can't share system folders");
        }

        OA oa = _ds.getOA_(soid);

        // can't share if a parent folder is already shared
        if (!_ss.isRoot_(soid.sidx())) throw new ExParentAlreadyShared();

        // can't share if a child folder is already shared
        Set<SIndex> descendants = _dss.getDescendantStores_(soid);
        if (!descendants.isEmpty()) throw new ExChildAlreadyShared();
        return oa;
    }

    /**
     * Pseudo-pause and make a call to SP to share the folder
     */
    private void callSP_(SID sid, String folderName, List<PBSubjectPermissions> roles,
            String emailNote, boolean suppressSharingRulesWarnings) throws Exception
    {
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "sp-share");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("sp-share");
            // NB: external shared folders are create by HdCreateRoot only
            _factSP.create()
                    .signInRemote()
                    .shareFolder(folderName, sid.toPB(), roles, emailNote, false,
                            suppressSharingRulesWarnings);
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }
    }

    private void convertToSharedFolder_(Path path, OA oa, SID sid)
            throws Exception
    {
        checkArgument(oa.isDir());

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

        l.debug("cleanup auto-join {} {}", sid, oaAnchor.name());

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
        checkArgument(!path.isEmpty());
        Path pathTemp = path;

        ResolvedPath from = _ds.resolve_(soid);

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
            _imc.createImmigrantRecursively_(from, soidChild, soidToRoot, oaChild.name(), MAP, t);
        }

        // Step 4: delete the root folder
        _od.deleteAndEmigrate_(soid, NOP, sid, t);
    }

    private void createNewStore(SOID soid, OID oidParent, SID sid, Path path, final Trans t)
            throws Exception
    {
        l.debug("new store: {} {}", sid, path);
        SOID soidAnchor = new SOID(soid.sidx(), SID.storeSID2anchorOID(sid));

        _ps.newFolder_(_ds.resolve_(soid)).updateSOID_(soidAnchor, t);

        OA oaAnchor = _ds.getOANullable_(soidAnchor);
        if (oaAnchor != null) {
            // any conflicting anchor created by auto-join upon ACL update should have been cleaned
            // before starting the conversion process
            checkState(oaAnchor.isExpelled(), "%s", oaAnchor);
            // move anchor to appropriate path (does not affect physical objects)
            _om.moveInSameStore_(oaAnchor.soid(), oidParent, path.last(), MAP, false, true, t);
        } else {
            // create anchor, root and trash, ...
            _oc.createMeta_(ANCHOR, soidAnchor, oidParent, path.last(), 0, MAP, true, true, t);
        }
    }
}