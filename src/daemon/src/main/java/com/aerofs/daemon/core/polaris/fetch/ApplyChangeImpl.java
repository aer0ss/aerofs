package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.PolarisContentVersionControl;
import com.aerofs.daemon.core.VersionUpdater;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ObjectSurgeon;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.migration.RemoteTreeCache;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.api.ObjectType;
import com.aerofs.daemon.core.polaris.api.RemoteChange;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ChangeEpochDatabase;
import com.aerofs.daemon.core.polaris.db.ContentChangesDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueWrapper;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase;
import com.aerofs.daemon.core.polaris.db.MetaBufferDatabase.BufferedChange;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase;
import com.aerofs.daemon.core.polaris.db.MetaChangesDatabase.MetaChange;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteChild;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.polaris.submit.MetaChangeSubmitter;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.ExpulsionDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

import static com.aerofs.daemon.core.ds.OA.Type.ANCHOR;
import static com.aerofs.daemon.core.ds.OA.Type.DIR;
import static com.aerofs.daemon.core.phy.PhysicalOp.APPLY;
import static com.aerofs.daemon.core.phy.PhysicalOp.MAP;
import static com.aerofs.daemon.core.phy.PhysicalOp.NOP;
import static com.google.common.base.Preconditions.checkState;

public class ApplyChangeImpl implements ApplyChange.Impl
{
    private static final Logger l = Loggers.getLogger(ApplyChangeImpl.class);

    private final TransManager _tm;
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final Expulsion _expulsion;
    private final MapAlias2Target _a2t;
    private final ObjectSurgeon _os;
    private final RemoteLinkDatabase _rpdb;
    private final MetaBufferDatabase _mbdb;
    private final MetaChangesDatabase _mcdb;
    private final ContentChangesDatabase _ccdb;
    private final RemoteContentDatabase _rcdb;
    private final CentralVersionDatabase _cvdb;
    private final MetaChangeSubmitter _submitter;
    private final StoreCreator _sc;
    private final StoreDeleter _sd;
    private final IMapSID2SIndex  _sid2sidx;
    private final ImmigrantCreator _imc;
    private final ExpulsionDatabase _exdb;
    private final StoreHierarchy _stores;
    private final LogicalStagingArea _sa;
    private final VersionUpdater _vu;
    private final ChangeEpochDatabase _cedb;
    private final ContentFetchQueueWrapper _cfqw;
    private final PolarisContentVersionControl _cvc;
    private final MetaDatabase _mdb;
    private final CfgLocalDID _localDID;

    // FIXME: remove once TS is burned
    private final CfgLocalUser _localUser;

    @Inject
    public ApplyChangeImpl(DirectoryService ds, IPhysicalStorage ps, Expulsion expulsion,
                           MapAlias2Target a2t, ObjectSurgeon os, RemoteLinkDatabase rpdb,
                           CentralVersionDatabase cvdb, MetaBufferDatabase mbdb,
                           MetaChangesDatabase mcdb, ContentChangesDatabase ccdb,
                           RemoteContentDatabase rcdb, MetaChangeSubmitter submitter,
                           StoreHierarchy stores, StoreCreator sc, IMapSID2SIndex sid2sidx,
                           ImmigrantCreator imc, ExpulsionDatabase exdb, StoreDeleter sd,
                           LogicalStagingArea sa, VersionUpdater vu, ChangeEpochDatabase cedb,
                           PolarisContentVersionControl cvc, ContentFetchQueueWrapper cfqw,
                           MetaDatabase mdb, TransManager tm, CfgLocalUser localUser, CfgLocalDID localDID)
    {
        _tm = tm;
        _ds = ds;
        _ps = ps;
        _expulsion = expulsion;
        _a2t = a2t;
        _os = os;
        _mdb = mdb;
        _cvdb = cvdb;
        _rpdb = rpdb;
        _mbdb = mbdb;
        _mcdb = mcdb;
        _ccdb = ccdb;
        _rcdb = rcdb;
        _cedb = cedb;
        _submitter = submitter;
        _sc = sc;
        _sd = sd;
        _sid2sidx = sid2sidx;
        _imc = imc;
        _exdb = exdb;
        _stores = stores;
        _sa = sa;
        _vu = vu;
        _cvc = cvc;
        _cfqw = cfqw;
        _localUser =  localUser;
        _localDID = localDID;
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
    public boolean hasMatchingLocalContent_(SOID soid, RemoteChange c, Trans t)
            throws SQLException, IOException {
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) return false;
        CA ca = oa.caMasterNullable();
        if (ca == null) return false;
        ContentHash h = _ds.getCAHash_(new SOKID(soid, KIndex.MASTER));
        if (h == null) return false;

        if (!h.equals(c.contentHash) || ca.length() != c.contentSize) {
            // NB: Handle case where transform is fetched before HTTP response in content submitter
            // and local change(s) happened after the submit so the local content doesn't actually
            // match anymore
            // Failure to bump the version in that case leads to persistent no-sync for the affected
            // file as the new version will forever fail to submit because of the version mismatch,
            // the old content cannot be fetched by any peer nor fetched from any other peer.
            // To avoid this undesirable behavior we pretend the content matched for ApplyChange
            // purposes but keep the local change in the submit queue.
            return _localDID.get().equals(c.originator);
        }

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
                 @Nullable OID migrant, long mergeBoundary, Trans t) throws Exception {
        OA.Type type = type(objectType);

        // insert, delete sequence can leave a buffered OID
        // a subsequent insert should not try to re-insert the object into the meta buffer
        boolean isBuffered = _mbdb.isBuffered_(new SOID(parent.sidx(), oidChild));
        if (isBuffered) return;

        // buffer inserts if:
        //   1. there are local changes
        //   2. the parent is already buffered
        //   3. a name conflict is detected
        //   4. a folder/anchor conflict is detected
        boolean shouldBuffer = _mbdb.isBuffered_(parent) || _mcdb.hasChanges_(parent.sidx())
                || neitherNullNor(_ds.getChild_(parent.sidx(), parent.oid(), name), oidChild)
                || (type == DIR && _ds.getOANullable_(new SOID(parent.sidx(), SID.folderOID2convertedAnchorOID(oidChild))) != null);

        if (shouldBuffer) {
            l.info("buffering {}{} {} {}", parent.sidx(), oidChild, migrant, mergeBoundary);
            _mbdb.insert_(parent.sidx(), oidChild, type, migrant, mergeBoundary, t);
        } else {
            applyInsert_(parent.sidx(), oidChild, parent.oid(), name, type, migrant, mergeBoundary, t);
        }
    }

    private static boolean neitherNullNor(OID a, OID b) {
        return a != null && !a.equals(b);
    }

    private void applyInsert_(SIndex sidx, OID oidChild, OID oidParent, String name, OA.Type type,
                              @Nullable OID migrant, long timestamp, Trans t) throws Exception {
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
                l.info("nop insert {}{} {}/{}", sidx, oidChild, oidParent, name);
                _mcdb.deleteChanges_(sidx, oidChild, t);
                return;
            } else if (_ds.isDeleted_(oa)) {
                l.info("restoring {}{} {}/{}", sidx, oidChild, parent.soid(), name);
            } else {
                l.info("move insert {}{} {}/{}", sidx, oidChild, oidParent, name);
            }
            applyMove_(parent, oa, name, timestamp, t);
            return;
        }
        _ds.createOA_(type, sidx, oidChild, oidParent, name, t);
        oa = _ds.getOA_(new SOID(sidx, oidChild));
        if (!oa.isExpelled()) {
            IPhysicalFolder pf = _ps.newFolder_(_ds.resolve_(oa));
            SIndex sidxFrom = migrant != null ? locate_(migrant, sidx) : null;
            if (sidxFrom != null) {
                migrate_(sidxFrom, migrant, sidx, oidChild, t);
            } else {
                if (migrant != null) l.info("unable to locate migrant {} {}", oidChild, migrant);
                if (oa.isDirOrAnchor()) pf.create_(APPLY, t);
            }
            if (oa.isAnchor()) {
                SID sid = SID.anchorOID2storeSID(oa.soid().oid());
                _sc.addParentStoreReference_(sid, oa.soid().sidx(), oa.name(), t);
                pf.promoteToAnchor_(sid, APPLY, t);
            }
        }
    }

    private SIndex locate_(OID migrant, SIndex expect) throws SQLException {
        if (migrant.isAnchor()) return null;
        for (SIndex sidx : _stores.getAll_()) {
            if (sidx.equals(expect)) continue;
            OA oa = _ds.getOANullable_(new SOID(sidx, migrant));
            if (oa == null || oa.isAnchor()) continue;
            OID dp = oa.isExpelled() ? _ds.deletedParent_(oa) : null;
            // ignore temporary migrated objects that will be deleted by a SHARE op
            if (dp != null && _sid2sidx.getLocalOrAbsentNullable_(SID.folderOID2convertedStoreSID(dp)) != null) {
                continue;
            }
            return sidx;
        }
        return null;
    }

    private void migrate_(SIndex sidxFrom, OID emigrant, SIndex sidxTo, OID immigrant, Trans t)
            throws Exception {
        OA oaFrom = _ds.getOA_(new SOID(sidxFrom, emigrant));
        OA oa = _ds.getOA_(new SOID(sidxTo, immigrant));

        if (oaFrom.type() != oa.type()) throw new ExProtocolError();

        if (oa.isFile()) {
            boolean hasChanges = _rcdb.hasRemoteChanges_(sidxTo, immigrant, 0L);
            l.info("migrate content {}{} {}{} {}", sidxFrom, emigrant, sidxTo, immigrant, hasChanges);
            // FIXME: if buffered, may have received some remote content for the immigrant already
            // this might overwrite them, which would be unfortunate
            try (IDBIterator<RemoteContent> it = _rcdb.list_(sidxFrom, emigrant)) {
                while (it.next_()) {
                    RemoteContent rc = it.get_();
                    _rcdb.insert_(sidxTo, immigrant, rc.version, rc.originator, rc.hash, rc.length, t);
                }
            }
            // nothing to restore if destination is expelled
            if (oa.isExpelled()) return;
            ResolvedPath pTo = _ds.resolve_(oa);
            if (oaFrom.isExpelled()) {
                // NB: nothing to restore on explicit expulsion
                if (_ds.isDeleted_(oaFrom)) {
                    restoreContent_(oaFrom, pTo, t);
                }
            } else {
                copy_(oaFrom, oa, pTo, t);
                // FIXME: version may reflect conflict branch state instead of MASTER
                Long v = _cvdb.getVersion_(sidxFrom, emigrant);
                if (v != null) {
                    _cvdb.setVersion_(sidxTo, immigrant, v, t);
                }
                if (oaFrom.caMasterNullable() != null && _ccdb.hasChange_(sidxFrom, emigrant)) {
                    // add content change and schedule submit
                    _vu.update_(new SOCID(sidxTo, immigrant, CID.CONTENT), t);
                }
            }
            Long v = _cvdb.getVersion_(sidxTo, immigrant);
            if (v != null && hasChanges) {
                // NB: only update BF if the immigrant already had some remote content entries
                // before being migrated, otherwise wait for either content submit or "obsolete"
                // UPDATE_CONTENT
                _cvc.setContentVersion_(sidxTo, immigrant, v, _cedb.getChangeEpoch_(sidxTo), t);
            }
            if (_rcdb.hasRemoteChanges_(sidxTo, immigrant, v != null ? v : 0)) {
                _cfqw.insert_(sidxTo, immigrant, t);
            }
        } else if (oa.isDir()) {
            l.info("migrate children {}{} {}{}", sidxFrom, emigrant, sidxTo, immigrant);
            _ps.newFolder_(_ds.resolve_(oa)).create_(APPLY, t);
            // TODO: linked storage, physical children not yet scanned?
            for (OID c : _ds.getChildren_(oaFrom.soid())) {
                if (_rpdb.getParent_(sidxFrom, c) != null) continue;
                OA cc = _ds.getOA_(new SOID(sidxFrom, c));
                OID mc = OID.generate();
                _ds.createOA_(cc.type(), sidxTo, mc, immigrant, cc.name(), t);
                // add meta change and schedule submit
                _vu.update_(new SOCID(sidxTo, mc, CID.META), t);
                migrate_(sidxFrom, c, sidxTo, mc, t);
            }
        } else {
            throw new ExProtocolError();
        }
    }

    private void restoreContent_(OA from, ResolvedPath pTo, Trans t) throws Exception {
        // need to make sure the deleted subtree is properly cleaned before attempting
        // to restore
        _sa.ensureStoreClean_(from.soid().sidx(), t);
        IPhysicalFile pf = _ps.newFile_(pTo, KIndex.MASTER);

        // retrieve path within deleted subtree (RemoteLinkDatabase)
        SIndex sidx = from.soid().sidx();
        OID oid = from.soid().oid();
        RemoteLink lnk;
        List<String> p = new ArrayList<>();
        // for local-only objects, first need to go up to first remote parent
        while (_rpdb.getParent_(sidx, oid) == null) {
            OA oa = _ds.getOA_(new SOID(sidx, oid));
            if (oa.parent().isTrash()) break;
            oid = oa.parent();
            p.add(oa.name());
        }
        // walk remote tree up to deleted subtree boundary
        while ((lnk = _rpdb.getParent_(sidx, oid)) != null) {
            oid = lnk.parent;
            p.add(lnk.name);
        }

        // NB: this may modify pf in case of NRO
        if (!_ps.restore_(from.soid(), oid, Lists.reverse(p), pf, t)) {
            l.warn("failed to restore content {}", from.soid());
        } else {
            // TODO: store content hash of history files to avoid recomputing
            ContentHash h = contentHash(pf);
            long length = pf.lengthOrZeroIfNotFile();
            long mtime = pf.lastModified();
            _ds.createCA_(pTo.soid(), KIndex.MASTER, t);
            _ds.setCA_(new SOKID(pTo.soid(), KIndex.MASTER), length, mtime, h, t);
            if (!matchContent_(pTo.soid().sidx(), pTo.soid().oid(), length, t)) {
                // preserve base version to avoid false conflicts
                // NB: central version is preserved for deleted objects specifically for this to
                // work. See PolarisContentVersionControl#fileExpelled_
                // FIXME: version may reflect conflict branch state instead of MASTER
                Long v = _cvdb.getVersion_(from.soid().sidx(), from.soid().oid());
                if (v != null) {
                    _cvdb.setVersion_(pTo.soid().sidx(), pTo.soid().oid(), v, t);
                }
                _vu.update_(new SOCID(pTo.soid(), CID.CONTENT), t);
            }
        }
    }

    private static ContentHash contentHash(IPhysicalFile pf) throws IOException {
        ContentHash h;
        try (DigestInputStream is = new DigestInputStream(pf.newInputStream(), BaseSecUtil.newMessageDigest())) {
            ByteStreams.copy(is, ByteStreams.nullOutputStream());
            h = new ContentHash(is.getMessageDigest().digest());
        }
        return h;
    }

    private void copy_(OA from, OA to, ResolvedPath pTo, Trans t) throws SQLException, IOException {
        ResolvedPath pFrom = _ds.resolve_(from);
        for (Entry<KIndex, CA> e : from.cas().entrySet()) {
            KIndex kidx = e.getKey();
            SOKID k = new SOKID(to.soid(), kidx);
            CA ca = e.getValue();
            ContentHash h = _ds.getCAHash_(new SOKID(from.soid(), kidx));
            l.info("copy {} {} {} {}", k, ca.length(), ca.mtime(), h);
            _ds.createCA_(to.soid(), kidx, t);
            _ds.setCA_(k, ca.length(), ca.mtime(), h, t);
            copy_(_ps.newFile_(pFrom, kidx), _ps.newFile_(pTo, kidx), ca.mtime(), t);
        }
    }

    // TODO: zero-copy copy for Block Storage
    private void copy_(IPhysicalFile from, IPhysicalFile to, long mtime, Trans t)
            throws SQLException, IOException {
        IPhysicalPrefix p = _ps.newPrefix_(to.sokid(), "copy");
        try (InputStream is = from.newInputStream();
             OutputStream os = p.newOutputStream_(false)) {
            ByteStreams.copy(is, os);
        }
        _ps.apply_(p, to, false, mtime, t);
    }

    @Override
    public void move_(SOID parent, OID oidChild, String name, long mergeBoundary,
               Trans t) throws Exception {
        SIndex sidx = parent.sidx();
        SOID soidChild = new SOID(parent.sidx(), oidChild);
        // buffer moves if:
        //   1. the child is already buffered
        //   2. the parent doesn't exist (but is buffered)
        //   3. a name conflict is detected
        boolean isBuffered = _mbdb.isBuffered_(soidChild);

        if (isBuffered) return;

        OA oaParent = _ds.getOANullable_(parent);
        boolean hasConflict = _ds.getChild_(sidx, parent.oid(), name) != null;

        OA oaChild = _ds.getOA_(soidChild);
        if (oaParent == null || hasConflict) {
            _mbdb.insert_(sidx, oidChild, oaChild.type(), null, mergeBoundary, t);
        } else {
            applyMove_(oaParent, oaChild, name, mergeBoundary, t);
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

        // TODO: attempt to restore last local version when moving out of trash
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
    public void delete_(SOID parent, OID oidChild, @Nullable OID migrant, Trans t) throws Exception {
        SOID soidChild = new SOID(parent.sidx(), oidChild);

        boolean isBuffered = _mbdb.isBuffered_(soidChild);

        if (migrant != null) {
            reconcileLocalChangesInMigratedTree_(parent.sidx(), oidChild, t);
        }

        // defer delete
        if (isBuffered) return;

        OA oaChild = _ds.getOANullable_(soidChild);
        if (oaChild == null) {
            l.warn("no such object to remove {} {}", parent, oidChild);
        } else {
            applyDelete_(oaChild, t);
        }
    }

    private void reconcileLocalChangesInMigratedTree_(SIndex sidx, OID oid, Trans t)
            throws SQLException, IOException, ExAlreadyExist {
        SOID migrated = new SOID(sidx, oid);
        Set<OID> discarded = new HashSet<>();
        RemoteTreeCache cache = new RemoteTreeCache(sidx, oid, _rpdb);
        Long last = _mcdb.getLastChangeIndex_(sidx);
        if (last == null) return;

        try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(sidx, Long.MIN_VALUE)) {
            while (it.next_()) {
                MetaChange c = it.get_();

                if (c.idx > last) break;

                // must deref alias to properly handle changes to objects moved out of migrated
                // subtree by an earlier change
                SOID soid = _a2t.dereferenceAliasedOID_(new SOID(sidx, c.oid));

                // object is under migrated subtree on polaris
                boolean from = cache.isInSharedTree(soid.oid());

                // object is under migrated subtree locally
                boolean to = _ds.resolve_(soid).soids.contains(migrated);

                // any meta change under the migrated subtree will be rejected by polaris since
                // every folder is now permanently LOCKED
                // however, if newParent is ultimately moved out of the subtree it will be recreated
                // under an alias and changes preceding that move should be preserved
                SOID parent = _a2t.dereferenceAliasedOID_(new SOID(sidx, c.newParent));
                boolean trans = cache.isInSharedTree(parent.oid())
                        && _ds.resolve_(parent).soids.contains(migrated);

                l.info("{} {}{}:{} {}:{} {} {} {}", c.idx, sidx, c.oid, soid.oid(),
                        c.newParent, parent.oid(), from, to, trans);

                if (from || trans || discarded.contains(c.oid) || discarded.contains(parent.oid())) {
                    l.info("discard change in migrated subtree {} {}{} {} {}",
                            c.idx, sidx, c.oid, c.newParent, c.newName);
                    // changes would be rejected by polaris as the migrated tree is locked to avoid
                    // races so they need to be discarded
                    // NB: if we support wholesale "restore" of deleted trees in the future we will
                    // have to reconcile the divergence at that point.
                    _mcdb.deleteChange_(sidx, c.idx, t);

                    if (_rpdb.getParent_(sidx, c.oid) == null) {
                        discarded.add(c.oid);
                    }
                }

                if (from && !to) {
                    OID cc = OID.generate();
                    l.info("obj locally moved out of migrated subtree {} {}{} {}", c.idx, c.sidx, c.oid, cc);
                    // NB: MetaChangeSubmitter will move to front if needed to account for already
                    // inserted children
                    _mcdb.insertChange_(sidx, cc, c.newParent, c.newName, t);
                    OA oa = _ds.getOA_(new SOID(sidx, c.oid));
                    alias_(sidx, cc, oa, t);
                } else if (!from && to && _rpdb.getParent_(sidx, c.oid) != null) {
                    l.info("obj locally moved into migrated subtree {} {}{}", c.idx, c.sidx, c.oid);
                    // ideally we'd cause a cross-store move instead but that's hard since at
                    // this point we have no idea what the parent is going to be in the
                    // destination store
                    // FIXME: iteration forever?
                    _mcdb.insertChange_(sidx, c.oid, OID.TRASH, c.oid.toStringFormal(), t);
                }
            }
        }
    }

    private void applyDelete_(OA oaChild, Trans t) throws Exception {
        OA oaTrash = _ds.getOA_(new SOID(oaChild.soid().sidx(), OID.TRASH));
        applyMove_(oaTrash, oaChild, oaChild.soid().oid().toStringFormal(), Long.MAX_VALUE, t);
    }

    private void cleanup_(SOID soid, Trans t) throws Exception {
        final RemoteTreeCache rtc = new RemoteTreeCache(soid.sidx(), soid.oid(), _rpdb);
        _ds.walk_(soid, true, new IObjectWalker<Boolean>() {
            @Nullable
            @Override
            public Boolean prefixWalk_(Boolean dummy, OA oa) throws Exception {
                if (oa.soid().equals(soid)) return true;
                SOID soid = oa.soid();
                if (!rtc.isInSharedTree(soid.oid())) {
                    // this case was seen for a linked TS at Finance IT after the Phoenix conversion
                    // while it is a little concerning it should ultimately be safe as any local
                    // files will be preserved in sync history and can be restored from there if
                    // they are not available in the destination folder on some remote peer
                    l.warn("cleanup {} not present in shared tree");
                }
                if (oa.isFile()) {
                    _rcdb.deleteUpToVersion_(soid.sidx(), soid.oid(), Long.MAX_VALUE, t);
                }
                return true;
            }

            @Override
            public void postfixWalk_(Boolean dummy, OA oa) throws Exception {
                if (oa.soid().equals(soid)) return;
                if (oa.isAnchor()) {
                    l.info("delete migrated anchor {}", oa.soid());
                    _ds.setOAParentAndName_(oa, _ds.getOA_(new SOID(oa.soid().sidx(), OID.TRASH)),
                            oa.soid().oid().toStringFormal(), t);
                } else {
                    l.info("erase migrated object {}", oa.soid());
                    _os.deleteOA_(oa.soid(), t);
                    if (oa.isFile()) _ccdb.deleteChange_(oa.soid().sidx(), oa.soid().oid(), t);
                }
            }
        });
    }

    @Override
    public void share_(SOID soid, Trans t) throws Exception {
        SID sid = SID.folderOID2convertedStoreSID(soid.oid());
        OID anchor = SID.storeSID2anchorOID(sid);
        SOID soidAnchor = new SOID(soid.sidx(), anchor);

        OA oaAnchor = _ds.getOANullable_(soidAnchor);
        if (oaAnchor != null) {
            // If the anchor is present before the folder is shared, which should only ever happen
            // after an unlink/reinstall wherein shared folders are restored from tag files, the
            // original folder is in the trash and the local subtree should match the remote one
            // exactly. To reach the same state as in regular sharing, we need to walk this tree
            // and erase all trace of these objects as they will eventually show up in the new
            // store (and may even already be there).
            l.warn("anchor present before SHARE: {} {}", soid, oaAnchor);
            cleanup_(soid, t);
            return;
        }

        OA oa = _ds.getOA_(soid);
        ResolvedPath p = _ds.resolve_(oa);

        if (!oa.parent().isTrash()) {
            // move folder out of the way
            // NB: must not put under trash just yet or expulsion status would be wrong
            // NB: prefix with forbidden character to ensure no conflict will arise
            // NB: MetaDatabase will ignore this disambiguation prefix when loading OA
            // NB: bypass DirectoryService to avoid triggering listeners and invalidating caches
            _mdb.setOAParentAndName_(soid.sidx(), soid.oid(), oa.parent(), "/" + oa.name(), t);

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

        // detect external root
        SIndex sidxTo = _sid2sidx.getNullable_(sid);

        if (sidxTo != null || _localUser.get().isTeamServerID()) {
            // anchor did not exist but store does: can happen on TS
            l.warn("store already present before SHARE {} {}", sid, sidxTo);
            // physical cleanup before anchor promotion
            _ps.deleteFolderRecursively_(p, APPLY, t);
            _ps.newFolder_(p).create_(APPLY, t);
        }

        // NB: ideally we wouldn't do that when the anchor is expelled but it's simpler to create
        // the store, do the regular migration and then delete it than it would be to handle an
        // expelled destination in every parts of the migration...
        if (sidxTo == null || !oa.isExpelled()) {
            _sc.addParentStoreReference_(sid, soid.sidx(), oa.name(), t);
        }
        if (!oa.isExpelled()) {
            _ps.newFolder_(p).updateSOID_(soidAnchor, t);
            IPhysicalFolder pf = _ps.newFolder_(p.substituteLastSOID(soidAnchor));
            pf.create_(MAP, t);
            pf.promoteToAnchor_(sid, MAP, t);
        }

        if (sidxTo != null || _localUser.get().isTeamServerID()) {
            // anchor did not exist but store does: can happen on TS
            cleanup_(soid, t);
        } else {

            sidxTo = _sid2sidx.get_(sid);
            SOID soidToRoot = new SOID(sidxTo, OID.ROOT);

            if (oa.isExpelled()) {
                // HACK: mark root dir as expelled to prevent ImmigrantCreator from trying to update
                // physical objects
                // NB: MUST bypass DirectoryService otherwise listeners may go crazy (e.g. SyncStat)
                // NB: this does not invalidate OA cache, which is fine because the root object was
                // just created when the store was and thus isn't in any cache. This is fragile and
                // could easily break in a refactoring...
                _mdb.setOAFlags_(soidToRoot, OA.FLAG_EXPELLED_ORG, t);
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

            for (OID c : _ds.getChildren_(soid)) {
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
        }

        // complete deletion of original folder
        if (!oa.parent().isTrash()) {
            OA trash = _ds.getOA_(new SOID(soid.sidx(), OID.TRASH));
            _ds.setOAParentAndName_(oa, trash, soid.oid().toStringFormal(), t);
            _expulsion.objectMoved_(p, soid, NOP, t);
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
        try (IDBIterator<MetaChange> it = _mcdb.getChangesSince_(sidxFrom, Long.MIN_VALUE)) {
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

                l.info("preserving move {} {} {} {}",
                        sidxTo, mc.oid, mc.newParent, mc.newName);
                _mcdb.insertChange_(sidxTo, mc.oid, mc.newParent, mc.newName, t);
            }
        }
    }

    @Override
    public boolean hasBufferedChanges_(SIndex sidx) throws SQLException {
        return _mbdb.getBufferedChange_(sidx, Long.MAX_VALUE) != null;
    }

    private final static int SPLIT_TRANS_THRESHOLD = 500;

    @Override
    public void applyBufferedChanges_(SIndex sidx, long timestamp)
            throws Exception {
        int n = 0;
        BufferedChange c;
        Trans t = _tm.begin_();
        try {
            while ((c = _mbdb.getBufferedChange_(sidx, timestamp)) != null) {
                applyBufferedChange_(sidx, c, timestamp, t);
                if (++n > SPLIT_TRANS_THRESHOLD) {
                    t.commit_();
                    t.end_();
                    n = 0;
                    t = _tm.begin_();
                }
                ProgressIndicators.get().incrementMonotonicProgress();
            }
            t.commit_();
        } finally {
            t.end_();
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
                applyInsert_(sidx, c.oid, OID.TRASH, c.oid.toStringFormal(), c.type, c.migrant, timestamp, t);
            } else {
                applyDelete_(oaChild, t);
            }
            return;
        }

        // ensure parent exists
        if (_ds.getOANullable_(new SOID(sidx, lnk.parent)) == null) {
            l.info("apply parent {}{}", sidx, lnk.parent);
            BufferedChange pc = _mbdb.getBufferedChange_(sidx, lnk.parent);
            checkState(pc != null && pc.type == DIR, "%s", pc);
            applyBufferedChange_(sidx, pc, timestamp, t);
        }

        String name = lnk.name;

        if (oaChild == null && c.type == DIR) {
            OA oaAnchor = _ds.getOANullable_(new SOID(sidx, SID.folderOID2convertedAnchorOID(c.oid)));
            if (oaAnchor != null) {
                // This should only happen in case a user unlinks and the shared folder is restored
                // on the first scan after reinstall.
                // Ideally we'd simply drop changes to the migrated tree but this is not safe as
                // object may move in and out of the subtree before it eventually gets shared.
                // Instead we have to insert the original folder directly inside the trash and
                // cleanup the subtree once the SHARE op is received.
                // NB: this may result in temporary violation of unicity invariant for OIDs (i.e.
                // a given OID is *NOT* supposed to be in multiple stores at once)
                // TODO: figure out how to adapt code to cope with that violation
                l.warn("buffered folder w/ matching anchor {}/{}:{}", lnk.parent, c.oid, name);
                applyInsert_(sidx, c.oid, OID.TRASH, c.oid.toStringFormal(), c.type,
                        null, timestamp, t);
                return;
            }
        }

        // check for conflict
        OID oidConflict = _ds.getChild_(sidx, lnk.parent, name);
        if (neitherNullNor(oidConflict, c.oid)) {
            RemoteLink clnk = _rpdb.getParent_(sidx, oidConflict);
            OA oaConflict = _ds.getOA_(new SOID(sidx, oidConflict));

            // TODO(phoenix): should NOT allow aliasing if local change submitted but not ack'ed
            // instead should throw and let the submitter perform aliasing on 409
            if (oaChild == null && clnk == null
                    && oaConflict.type() == c.type && c.type != OA.Type.ANCHOR) {
                // TODO: migration?
                alias_(sidx, c.oid, oaConflict, t);

                // no need to insert the child
                return;
            } else {
                name = resolveNameConflict_(sidx, lnk, oaConflict, timestamp, t);
            }
        }

        // apply remote change
        // NB: applyInsert_ will do a move if the object already exists
        applyInsert_(sidx, c.oid, lnk.parent, name, c.type, c.migrant, timestamp, t);
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

        OA oaParent;
        // FIXME: interleaved submission of complex name conflicts (e.g. name-shifting)
        // can result in changes being reverted on both side
        // This is suboptimal (one side should win) and can occasionally break meta/meta conflict
        // syncdet tests.
        // One way of improving this would be for polaris to be enforce more restrictions on moves,
        // e.g. tagging (parent, name) pairs with a logical timestamp to prevent such interleaved
        // submissions and close some races in conflict resolution. A purely client-side solution
        // would however be preferred.
        if (clnk != null && _ds.getChild_(sidx, clnk.parent, clnk.name) == null
                && _ds.getOANullable_(new SOID(sidx, clnk.parent)) != null) {
            // conflict OID was moved locally and its remote location is free: move it back
            l.info("revert local change: {} -> {}/{}", oaConflict.soid(), clnk.parent, clnk.name);

            _mcdb.deleteChanges_(sidx, oaConflict.soid().oid(), t);

            oaParent = _ds.getOA_(new SOID(sidx, clnk.parent));
            targetName = clnk.name;
        } else {
            l.info("rename local {} {}", oaConflict.soid(), targetName);

            // NB: we do NOT remove local changes:
            //  - if the object is not yet known to polaris, doing so could reorder the initial
            //    insert of a folder after insertion of its children which would result in a
            //    persistent no-sync
            //  - if the object is known to polaris and we didn't simply revert the conflicting
            //    local changes it means the remote location is not free which in turns means we
            //    have a local transform that targets this location and it will be rejected unless
            //    we first submit a local transform moving the local object out of its location
            // Instead we rely on MetaChangeSubmitter#onConflict_ to correct any intermediate name
            // conflicts as needed.

            oaParent = _ds.getOA_(new SOID(sidx, lnk.parent));
            _mcdb.insertChange_(sidx, oaConflict.soid().oid(), lnk.parent, targetName, t);
        }

        ResolvedPath pConflict = _ds.resolve_(oaConflict);
        _ds.setOAParentAndName_(oaConflict, oaParent, targetName, t);
        _expulsion.objectMoved_(pConflict, oaConflict.soid(), PhysicalOp.APPLY, t);
        return lnk.name;
    }

    private String nextNonConflictingName_(SIndex sidx, OID parent, String name) throws SQLException {
        // TODO: binary search
        String targetName = name;
        do {
            targetName = FileUtil.nextFileName(targetName);
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

            CA ca = oaConflict.caMasterNullable();
            boolean match = ca != null && matchContent_(sidx, oid, ca.length(), t);

            if (match) {
                // when aliases are resolved after buffering the local content may match a
                // remote content that was received before buffered changes were resolved
                // when that happens we need to update Bloom Filters and content change epoch
                // since ApplyChange won't have taken care of it...
                _cvc.setContentVersion_(sidx, oid, _cvdb.getVersion_(sidx, oid),
                        _cedb.getChangeEpoch_(sidx), t);
            }

            if (_ccdb.deleteChange_(sidx, oaConflict.soid().oid(), t) && !match) {
                _ccdb.insertChange_(sidx, oid, t);
            }
        } else {
            _ps.newFolder_(pConflict).updateSOID_(new SOID(sidx, oid), t);
        }
    }

    private boolean matchContent_(SIndex sidx, OID oid, long length, Trans t) throws SQLException {
        ContentHash h = _ds.getCAHash_(new SOKID(sidx, oid, KIndex.MASTER));

        try (IDBIterator<RemoteContent> it = _rcdb.list_(sidx, oid)) {
            while (it.next_()) {
                RemoteContent rc = it.get_();
                if (rc.hash.equals(h) && rc.length == length) {
                    _cvdb.setVersion_(sidx, oid, rc.version, t);
                    _rcdb.deleteUpToVersion_(sidx, oid, rc.version, t);
                    return true;
                }
            }
        }
        return false;
    }
}
