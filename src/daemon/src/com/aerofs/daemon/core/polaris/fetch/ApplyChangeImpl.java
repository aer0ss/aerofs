package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.*;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.migration.RemoteTreeCache;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase.BufferedChange;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase.MetaChange;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteChild;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.ExpulsionDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.aerofs.daemon.core.ds.OA.Type.ANCHOR;
import static com.aerofs.daemon.core.phy.PhysicalOp.APPLY;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import static com.google.common.base.Preconditions.checkState;

public class ApplyChangeImpl implements ApplyChange.Impl
{
    private static final Logger l = Loggers.getLogger(ApplyChangeImpl.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final Expulsion _expulsion;
    private final MapAlias2Target _a2t;
    private final ObjectSurgeon _os;
    private final RemoteLinkDatabase _rpdb;
    private final MetaBufferDatabase _mbdb;
    private final MetaChangesDatabase _mcdb;
    private final ContentChangesDatabase _ccdb;
    private final MetaChangeSubmitter _submitter;
    private final StoreCreator _sc;
    private final StoreDeleter _sd;
    private final IMapSID2SIndex  _sid2sidx;
    private final ImmigrantCreator _imc;
    private final ExpulsionDatabase _exdb;

    @Inject
    public ApplyChangeImpl(DirectoryService ds, IPhysicalStorage ps, Expulsion expulsion,
                           MapAlias2Target a2t, ObjectSurgeon os, RemoteLinkDatabase rpdb,
                           MetaBufferDatabase mbdb, MetaChangesDatabase mcdb,
                           ContentChangesDatabase ccdb, MetaChangeSubmitter submitter,
                           StoreCreator sc, IMapSID2SIndex sid2sidx, ImmigrantCreator imc,
                           ExpulsionDatabase exdb, StoreDeleter sd)
    {
        _ds = ds;
        _ps = ps;
        _expulsion = expulsion;
        _a2t = a2t;
        _os = os;
        _rpdb = rpdb;
        _mbdb = mbdb;
        _mcdb = mcdb;
        _ccdb = ccdb;
        _submitter = submitter;
        _sc = sc;
        _sd = sd;
        _sid2sidx = sid2sidx;
        _imc = imc;
        _exdb = exdb;
    }

    @Override
    public boolean ackMatchingSubmittedMetaChange_(SIndex sidx, RemoteChange rc, RemoteLink lnk,
                                            Trans t) throws Exception
    {
        return _submitter.ackMatchingSubmittedMetaChange_(sidx, rc, lnk, t);
    }

    private static OA.Type type(ObjectType t) throws ExProtocolError {
        switch (t) {
            case FILE:
                return OA.Type.FILE;
            case FOLDER:
                return OA.Type.DIR;
            case STORE:
                return OA.Type.ANCHOR;
            default:
                throw new ExProtocolError();
        }
    }

    @Override
    public boolean newContent_(SOID soid, RemoteChange c, Trans t)
            throws SQLException, IOException {
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) return false;
        CA ca = oa.caMasterNullable();
        if (ca == null) return false;
        ContentHash h = _ds.getCAHash_(new SOKID(soid, KIndex.MASTER));
        if (h == null || !h.equals(c.contentHash) || ca.length() != c.contentSize) return false;

        // local content matches remote change
        // -> discard local change if any
        _ccdb.deleteChange_(soid.sidx(), soid.oid(), t);

        // -> delete conflict branch if any
        if (oa.cas().size() > 1) {
            checkState(oa.cas().size() == 2);
            KIndex kidx = KIndex.MASTER.increment();
            l.info("delete branch {}k{}", soid, kidx);
            _ds.deleteCA_(soid, kidx, t);
            _ps.newFile_(_ds.resolve_(oa), kidx).delete_(PhysicalOp.APPLY, t);
        }
        return true;
    }

    @Override
    public void insert_(SOID parent, OID oidChild, String name, ObjectType objectType,
                 long mergeBoundary, Trans t) throws Exception {
        OA.Type type = type(objectType);
        // buffer inserts if:
        //   1. there are local changes
        //   2. the parent is already buffered
        //   3. a name conflict is detected
        boolean shouldBuffer = _mbdb.isBuffered_(parent) || _mcdb.hasChanges_(parent.sidx())
                || _ds.getChild_(parent.sidx(), parent.oid(), name) != null;

        if (shouldBuffer) {
            _mbdb.insert_(parent.sidx(), oidChild, type, mergeBoundary, t);
        } else {
            applyInsert_(parent.sidx(), oidChild, parent.oid(), name, type, mergeBoundary, t);
        }
    }

    private void applyInsert_(SIndex sidx, OID oidChild, OID oidParent, String name, OA.Type type,
                              long timestamp, Trans t) throws Exception {
        OA oa = _ds.getOANullable_(new SOID(sidx, oidChild));
        if (oa != null) {
            OA parent = _ds.getOANullable_(new SOID(sidx, oidParent));
            if (parent == null) {
                l.warn("invalid insert {}{} {}/{} {}", sidx, oidChild, oidParent, name, type);
                throw new ExProtocolError();
            } else if (oa.type() != type) {
                l.warn("spurious insert {}{} {}/{} {}", sidx, oidChild, oidParent, name, type);
                // FIXME(phoenix): handle the unlikely event of a random OID collision
                //  -> ensure local object is not known remotely (no version)
                //  -> assign a new random OID to the local object
                throw new ExProtocolError();
            } else if (oa.parent().equals(oidParent) && oa.name().equals(name)) {
                l.info("nop insert {}{} {}/name", sidx, oidParent, oidChild, name);
                return;
            } else if (_ds.isDeleted_(oa)) {
                l.info("restoring {}{} {}/{}", sidx, oidChild, parent.soid(), name);
            } else {
                l.info("move insert {}{} {}/name", sidx, oidParent, oidChild, name);
            }
            applyMove_(parent, oa, name, timestamp, t);
            return;
        }
        _ds.createOA_(type, sidx, oidChild, oidParent, name, t);
        oa = _ds.getOA_(new SOID(sidx, oidChild));
        if (!oa.isExpelled()) {
            IPhysicalFolder pf = _ps.newFolder_(_ds.resolve_(oa));
            if (oa.isDirOrAnchor()) {
                pf.create_(APPLY, t);
            }
            if (oa.isAnchor()) {
                SID sid = SID.anchorOID2storeSID(oa.soid().oid());
                _sc.addParentStoreReference_(sid, oa.soid().sidx(), oa.name(), t);
                pf.promoteToAnchor_(sid, APPLY, t);
            }
        }
    }

    @Override
    public void move_(SOID parent, OID oidChild, String name, long mergeBoundary,
               Trans t) throws Exception {
        SIndex sidx = parent.sidx();
        SOID soidChild = new SOID(parent.sidx(), oidChild);
        // buffer moves if:
        //   1. the child is already buffered
        //   2. a name conflict is detected
        boolean isBuffered = _mbdb.isBuffered_(soidChild);
        boolean hasConflict = _ds.getChild_(sidx, parent.oid(), name) != null;

        if (isBuffered) return;

        OA oaChild = _ds.getOA_(soidChild);
        if (hasConflict) {
            _mbdb.insert_(sidx, oidChild, oaChild.type(), mergeBoundary, t);
        } else {
            applyMove_(_ds.getOA_(parent), oaChild, name, mergeBoundary, t);
        }
    }

    private void applyMove_(OA oaParent, OA oaChild, String name, long timestamp, Trans t)
            throws Exception {
        if (resolveCycle_(oaParent.soid(), oaChild.soid(), timestamp, t)) {
            oaParent = _ds.getOA_(oaParent.soid());
            oaChild = _ds.getOA_(oaChild.soid());
        }
        ResolvedPath pOld = _ds.resolve_(oaChild);

        _ds.setOAParentAndName_(oaChild, oaParent, name, t);

        _expulsion.objectMoved_(pOld, oaChild.soid(), PhysicalOp.APPLY, t);

        // all local meta changes affecting the same child are now obsolete
        _mcdb.deleteChanges_(oaChild.soid().sidx(), oaChild.soid().oid(), t);
    }

    private boolean resolveCycle_(SOID parent, SOID child, long timestamp, Trans t)
            throws Exception {
        boolean resolved = false;
        while (true) {
            ResolvedPath pParent = _ds.resolve_(parent);
            ResolvedPath pChild = _ds.resolve_(child);

            if (!pParent.isStrictlyUnder(pChild)) break;

            l.info("breaking cyclic move {} -> {}", pChild, pParent);

            // Here be cycles!
            //
            // Suppose you start out with
            //
            // r
            // |_n1o1
            // |_n2o2
            //     |_n3o3
            //
            //
            // And two devices make the following changes:
            //
            //      A       |       B
            // -------------|---------------
            // r            |   r
            // |_n1o1       |   |_n2o2
            //    |_n2o2    |      |_n3o3
            //       |_n3o3 |         |_n1o1
            //
            //
            // One of the changes is going to be accepted by polaris, the other rejected because it
            // would create a cycle. Then, the device whose change was rejected will receive the
            // accepted change and will have to resolve the cycle locally.
            //
            // e.g.: A receives move o1 under o3
            //       B receives move o2 under o1
            //
            // Resolution:
            // Walk upwards from the bottom of the cycle, find the first object along that path
            // whose local parent doesn't match its remote parent and move it back to where it
            // belongs (potentially causing a recursive cycle resolution...)
            // Then resolve the object paths again and keep at it until the cycle is broken.
            for (int i = pParent.soids.size() - 1; i >= 0; i--) {
                SOID soid = pParent.soids.get(i);
                OA oa = _ds.getOA_(soid);
                RemoteLink lnk = _rpdb.getParent_(soid.sidx(), soid.oid());
                if (lnk.parent.equals(oa.parent())) continue;
                OA rp = _ds.getOA_(new SOID(soid.sidx(), lnk.parent));
                String name = lnk.name;
                OID conflict = _ds.getChild_(soid.sidx(), lnk.parent, name);
                if (conflict != null) {
                    OA oaConflict = _ds.getOA_(new SOID(soid.sidx(), conflict));
                    name = resolveNameConflict_(soid.sidx(), lnk, oaConflict, 0L, t);
                }
                applyMove_(rp, oa, name, timestamp, t);
                break;
            }
            resolved = true;
        }
        return resolved;
    }

    @Override
    public void delete_(SOID parent, OID oidChild, Trans t) throws Exception {
        SOID soidChild = new SOID(parent.sidx(), oidChild);

        boolean isBuffered = _mbdb.isBuffered_(soidChild);

        // defer delete
        if (isBuffered) return;

        OA oaChild = _ds.getOANullable_(soidChild);
        if (oaChild == null) {
            l.warn("no such object to remove {} {}", parent, oidChild);
        } else {
            applyDelete_(oaChild, t);
        }
    }

    void applyDelete_(OA oaChild, Trans t) throws Exception {
        OA oaTrash = _ds.getOA_(new SOID(oaChild.soid().sidx(), OID.TRASH));
        applyMove_(oaTrash, oaChild, oaChild.soid().oid().toStringFormal(), Long.MAX_VALUE, t);
    }

    @Override
    public void share_(SOID soid, Trans t) throws Exception
    {
        SID sid = SID.folderOID2convertedStoreSID(soid.oid());
        OID anchor = SID.storeSID2anchorOID(sid);
        SOID soidAnchor = new SOID(soid.sidx(), anchor);

        OA oa = _ds.getOA_(soid);
        ResolvedPath p = _ds.resolve_(oa);

        if (!oa.parent().isTrash()) {
            // move folder out of the way
            // NB: must not put under trash just yet or expulsion status would be wrong
            // NB: use forbidden character in prefix to avoid conflict
            _ds.setOAParentAndName_(oa, _ds.getOA_(new SOID(soid.sidx(), oa.parent())),
                    "/" + soid.oid().toStringFormal(), t);

            // create anchor
            _ds.createOA_(ANCHOR, soid.sidx(), anchor, oa.parent(), oa.name(), t);
        } else {
            // create anchor directly in trash...
            _ds.createOA_(ANCHOR, soid.sidx(), anchor, oa.parent(), anchor.toStringFormal(), t);
        }

        // preserve explicit expulsion state
        if (oa.isSelfExpelled()) {
            _ds.setExpelled_(soidAnchor, true, t);
            _exdb.insertExpelledObject_(soidAnchor, t);
            _exdb.deleteExpelledObject_(soid, t);
        }

        // NB: ideally we wouldn't do that when the anchor is expelled but it's simpler to create
        // the store, do the regular migration and then delete it than it would be to handle an
        // expelled destination in every parts of the migration...
        _sc.addParentStoreReference_(sid, soid.sidx(), oa.name(), t);

        SIndex sidxTo = _sid2sidx.get_(sid);
        SOID soidToRoot = new SOID(sidxTo, OID.ROOT);

        if (oa.isExpelled()) {
            // HACK: mark root dir as expelled to prevent ImmigrantCreator from trying to update
            // physical objects
            _ds.setExpelled_(soidToRoot, true, t);
        } else {
            _ps.newFolder_(p).updateSOID_(soidAnchor, t);
            IPhysicalFolder pf = _ps.newFolder_(p.substituteLastSOID(soidAnchor));
            pf.create_(MAP, t);
            pf.promoteToAnchor_(sid, MAP, t);
        }

        // meta changes for the original folder should be moved to anchor
        _mcdb.updateChanges_(soid.sidx(), soid.oid(), anchor, t);

        /**
         * here be dragons
         *
         * assumption:
         * SHARE op is received once the migration is completed server-side, i.e. as if the local
         * snapshot of the remote tree (RemoteLinkDatabase) had been atomically migrated
         *
         * 1. recursively walk local migrated subtree
         *      - is object is remote migrated subtree
         *          yes: preserve OID, version, and all meta changes
         *          no:
         *              - is object know remotely?
         *                  yes: assign new OID in target store, delete in source store
         *                  no: preserve OID, consolidate meta changes to a single insert
         * 2. recursively walk remote migrated subtree
         *      - replicate in target store
         *      - check if object is in local migrated tree (i.e. target store)
         *          yes: nothing to do
         *          no:  assign new OID in source store, delete in target store
         * 3. go through local meta changes to source store in order, filtering out those that do
         *    not affect objects in migrated subtree
         *      - check if new parent is in migrated subtree
         *          yes: move change to target store
         *          no:  drop change FIXME: this is not 100% safe
         *
         *
         * issues:
         *   - complex and somewhat brittle
         *   - full subtree walk in a single transaction
         *      -> slow, blocking processing of other core events
         *      -> risk of crash wo/ incremental progress
         */
        // migrate children
        // TODO: deferred/incremental
        RemoteTreeCache cache = new RemoteTreeCache(soid.sidx(), soid.oid(), _rpdb);

        for (OID c :_ds.getChildren_(soid)) {
            SOID soidChild = new SOID(soid.sidx(), c);
            OA oaChild = _ds.getOA_(soidChild);
            _imc.createImmigrantRecursively_(p, soidChild, soidToRoot, oaChild.name(), MAP, cache, t);
        }

        copyRemoteTree_(soid.sidx(), soid.oid(), OID.ROOT, sidxTo, t);
        moveMetaChanges_(soid.sidx(), soid.oid(), sidxTo, cache, t);

        // remove store if the anchor is expelled
        if (oa.isExpelled()) {
            _sd.removeParentStoreReference_(sidxTo, soid.sidx(), p, MAP, t);
        }

        // complete deletion of original folder
        if (!oa.parent().isTrash()) {
            OA trash = _ds.getOA_(new SOID(soid.sidx(), OID.TRASH));
            _ds.setOAParentAndName_(oa, trash, soid.oid().toStringFormal(), t);
            _expulsion.objectMoved_(p, soid, PhysicalOp.NOP, t);
        }
    }

    private void copyRemoteTree_(SIndex sidxFrom, OID root, OID parent, SIndex sidxTo, Trans t)
            throws SQLException, IOException, ExProtocolError {
        List<OID> folders = new ArrayList<>();
        try (IDBIterator<RemoteChild> it = _rpdb.listChildren_(sidxFrom, root)) {
            while (it.next_()) {
                RemoteChild rc = it.get_();
                _rpdb.insertParent_(sidxTo, rc.oid, parent, rc.name, -1, t);
                OA oa = _ds.getOANullable_(new SOID(sidxTo, rc.oid));
                if (oa == null) {
                    oa = handleLocallyMigrated_(sidxFrom, sidxTo, rc.oid, parent, rc.name, t);
                }
                if (oa.isDir()) {
                    folders.add(rc.oid);
                }
            }
        }

        // IMPORTANT: recurse outside of iterator
        for (OID oid : folders) copyRemoteTree_(sidxFrom, oid, oid, sidxTo, t);
    }

    private OA handleLocallyMigrated_(SIndex sidxFrom, SIndex sidxTo, OID oid, OID parent, String name, Trans t)
            throws SQLException, IOException, ExProtocolError {
        OA oa = _ds.getOANullable_(new SOID(sidxFrom, oid));
        l.info("{}->{}{} {} locally moved out of shared subtree: {}",
                sidxFrom, sidxTo, oid, parent, oa);
        if (oa == null) throw new ExProtocolError();

        if (_ds.isDeleted_(oa)) {
            // NB: discard the object and reparent any children to the TRASH folder
            _os.deleteOA_(oa.soid(), t);
            // make sure meta changes with this object as parent are treated as deletions
            if (oa.isDir()) _a2t.add_(oa.soid(), new SOID(sidxFrom, OID.TRASH), t);
        } else {
            int n = 0;
            while (true) {
                OID alias = OID.generate();
                try {
                    // alias object in source store to a random OID
                    alias_(sidxFrom, alias, oa, t);
                } catch (ExAlreadyExist e) {
                    l.info("unlucky OID choice", e);
                    if (++n < 5) continue;
                    throw new ExProtocolError();
                }
                _mcdb.insertChange_(sidxFrom, alias, oa.parent(), oa.name(), t);
                if (oa.isFile() && oa.caMasterNullable() != null) {
                    _ccdb.insertChange_(sidxFrom, alias, t);
                }
                break;
            }
        }
        // scrub all references to OID in source store
        // TODO(phoenix): is there more to scrub?

        // insert object under trash in shared folder
        // NB: keep under remote parent if already deleted
        OA oaParent = _ds.getOANullable_(new SOID(sidxTo, parent));
        OID newParent;
        String newName;
        if (oaParent != null && oaParent.isExpelled() && _ds.isDeleted_(oaParent)) {
            // remote parent is already deleted (presumably because it was moved out)
            // -> avoid redundant local changes
            newParent = parent;
            newName = name;
        } else {
            newParent = OID.TRASH;
            newName = oid.toStringFormal();
            _mcdb.insertChange_(sidxTo, oid, OID.TRASH, oid.toStringFormal(), t);
        }
        try {
            _ds.createOA_(oa.type(), sidxTo, oid, newParent, newName, t);
        } catch (ExNotFound|ExAlreadyExist e) {
            throw new IllegalStateException(e);
        }
        _imc.preserveVersions_(sidxFrom, oid, oa.type(), sidxTo, t);
        return oa;
    }

    private void moveMetaChanges_(SIndex sidxFrom, OID root, SIndex sidxTo, RemoteTreeCache c,
                                  Trans t) throws SQLException {
        try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(sidxFrom, -1)) {
            while (it.next_()) {
                MetaChange mc = it.get_();
                if (!c.isInSharedTree(mc.oid)) {
                    // FIXME(phoenix): what if the newParent is an object inside shared tree
                    continue;
                }
                OA oa = _ds.getOANullable_(new SOID(sidxTo, mc.oid));
                checkState(oa != null);
                _mcdb.deleteChange_(sidxFrom, mc.idx, t);
                // changes under root folder should be moved under ROOT object
                if (root.equals(mc.newParent)) {
                    mc.newParent = OID.ROOT;
                } else if (!c.isInSharedTree(mc.newParent)) {
                    // FIXME(phoenix): dropping moves out of the shared tree *may* introduce conflicts
                    // replace intermediate outbound moves by non-conflicting inside moves
                    //mc.newParent = ?;
                    //mc.newName = ?;
                    l.warn("dropping intermediate out-of-subtree move {} {} {} {} {}",
                            sidxTo, mc.idx, mc.oid, mc.newParent, mc.newName);
                    continue;
                }

                l.debug("preserving move {} {} {} {}",
                        sidxTo, mc.oid, mc.newParent, mc.newName);
                _mcdb.insertChange_(sidxTo, mc.oid, mc.newParent, mc.newName, t);
            }
        }
    }

    public void applyBufferedChanges_(SIndex sidx, long timestamp, Trans t)
            throws Exception {
        BufferedChange c;
        while ((c = _mbdb.getBufferedChange_(sidx, timestamp)) != null) {
            applyBufferedChange_(sidx, c, timestamp, t);
        }
    }

    private void applyBufferedChange_(SIndex sidx, BufferedChange c, long timestamp, Trans t)
            throws Exception {
        RemoteLink lnk = _rpdb.getParent_(sidx, c.oid);
        OA oaChild = _ds.getOANullable_(new SOID(sidx, c.oid));

        // mark change as applied immediately because early returns
        _mbdb.remove_(sidx, c.oid, t);

        if (lnk == null) {
            if (oaChild == null) {
                applyInsert_(sidx, c.oid, OID.TRASH, c.oid.toStringFormal(), c.type, timestamp, t);
            } else {
                applyDelete_(oaChild, t);
            }
            return;
        }

        // ensure parent exists
        OA oaParent = _ds.getOANullable_(new SOID(sidx, lnk.parent));
        if (oaParent == null) {
            l.info("apply parent {}{}", sidx, lnk.parent);
            checkState(_mbdb.isBuffered_(new SOID(sidx, lnk.parent)));
            // TODO(phoenix): watch for cycles
            applyBufferedChange_(sidx, new BufferedChange(lnk.parent, OA.Type.DIR), timestamp, t);
            oaParent = _ds.getOA_(new SOID(sidx, lnk.parent));
        }

        String name = lnk.name;

        // check for conflict
        OID oidConflict = _ds.getChild_(sidx, lnk.parent, name);
        if (oidConflict != null) {
            RemoteLink clnk = _rpdb.getParent_(sidx, oidConflict);
            OA oaConflict = _ds.getOA_(new SOID(sidx, oidConflict));

            // TODO(phoenix): should NOT allow aliasing if local change submitted but not ack'ed
            // instead should throw and let the submitter perform aliasing on 409
            if (oaChild == null && clnk == null
                    && oaConflict.type() == c.type && c.type != OA.Type.ANCHOR) {
                alias_(sidx, c.oid, oaConflict, t);

                // no need to insert the child
                return;
            } else {
                name = resolveNameConflict_(sidx, lnk, oaConflict, timestamp, t);
            }
        }

        // apply remote change
        if (oaChild == null) {
            applyInsert_(sidx, c.oid, lnk.parent, name, c.type, timestamp, t);
        } else {
            applyMove_(oaParent, oaChild, name, timestamp, t);
        }
    }

    private String resolveNameConflict_(SIndex sidx, RemoteLink lnk, OA oaConflict, long timestamp,
                                        Trans t) throws Exception {
        String targetName = nextNonConflictingName_(sidx, lnk.parent, lnk.name);

        RemoteLink clnk = _rpdb.getParent_(sidx, oaConflict.soid().oid());
        // If a buffered object conflict with an object known to have been updated after the
        // buffer merge window we can safely leave the conflicting object unchanged and instead
        // issue a non-conflicting name to the remote object as it is guaranteed to be updated
        // at some point in the future
        if (clnk != null && clnk.logicalTimestamp > timestamp) {
            l.info("rename remote {}", targetName);
            return targetName;
        }

        l.info("rename local {} {}", oaConflict.soid(), targetName);

        OA oaParent = _ds.getOA_(new SOID(sidx, lnk.parent));
        ResolvedPath pConflict = _ds.resolve_(oaConflict);
        _ds.setOAParentAndName_(oaConflict, oaParent, targetName, t);

        _expulsion.objectMoved_(pConflict, oaConflict.soid(), PhysicalOp.APPLY, t);

        _mcdb.insertChange_(sidx, oaConflict.soid().oid(), lnk.parent, targetName, t);
        return lnk.name;
    }

    private String nextNonConflictingName_(SIndex sidx, OID parent, String name) throws SQLException {
        // TODO: binary search
        String targetName = name;
        do {
            targetName = Util.nextFileName(targetName);
        } while (_ds.getChild_(sidx, parent, targetName) != null);
        return targetName;
    }

    private void alias_(SIndex sidx, OID oid, OA oaConflict, Trans t)
            throws SQLException, IOException, ExAlreadyExist {
        l.info("alias {} {} {}", sidx, oid, oaConflict.soid().oid());

        ResolvedPath pConflict = _ds.resolve_(oaConflict);
        _os.replaceOID_(oaConflict.soid(), new SOID(sidx, oid), t);

        _mcdb.deleteChanges_(sidx, oaConflict.soid().oid(), t);

        // TODO: expire alias entries (use local meta change idx)
        // any meta change made after the alias event will use the new OID
        // the only place where the mapping of old oid to new OID is needed in when submitting
        // meta changes
        _a2t.add_(oaConflict.soid(), new SOID(sidx, oid), t);

        if (oaConflict.type() == OA.Type.FILE) {
            for (KIndex kidx : oaConflict.cas().keySet()) {
                _ps.newFile_(pConflict, kidx).updateSOID_(new SOID(sidx, oid), t);

                // TODO: delete *all* prefixes
                _ps.newPrefix_(new SOKID(oaConflict.soid(), kidx), null).delete_();
            }
            if (_ccdb.deleteChange_(sidx, oaConflict.soid().oid(), t)) {
                _ccdb.insertChange_(sidx, oid, t);
            }
        } else {
            _ps.newFolder_(pConflict).updateSOID_(new SOID(sidx, oid), t);
        }
    }
}
