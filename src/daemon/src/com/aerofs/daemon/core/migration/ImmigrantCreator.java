package com.aerofs.daemon.core.migration;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.*;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.PolarisStore;
import com.aerofs.daemon.lib.db.ExpulsionDatabase;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.*;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.sql.SQLException;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * This class operates at a higher level than ObjectMover/Deleter/Creator, which in turn are at
 * a higher level than ImmigrantDetector. That is, they have the dependency of:
 *
 * ImmigrantCreator -> ObjectMover/Deleter/Creator -> ImmigrantDetector
 */
public class ImmigrantCreator
{
    private final static Logger l = Loggers.getLogger(ImmigrantCreator.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final ObjectCreator _oc;
    private final ObjectMover _om;
    private final ObjectDeleter _od;
    private final ObjectSurgeon _os;
    private final IMapSIndex2SID _sidx2sid;
    private final CfgUsePolaris _polaris;
    private final RemoteLinkDatabase _rldb;
    private final CentralVersionDatabase _cvdb;
    private final MetaChangesDatabase  _mcdb;
    private final ContentChangesDatabase _ccdb;
    private final RemoteContentDatabase _rcdb;
    private final ExpulsionDatabase _exdb;
    private final ContentFetchQueueDatabase _cfqdb;
    private final MapSIndex2Store _sidx2s;

    @Inject
    public ImmigrantCreator(DirectoryService ds, IPhysicalStorage ps, IMapSIndex2SID sidx2sid,
            ObjectMover om, ObjectDeleter od, ObjectCreator oc, ObjectSurgeon os,
            CfgUsePolaris polaris, RemoteLinkDatabase rldb, MetaChangesDatabase mcdb,
            ContentChangesDatabase ccdb, RemoteContentDatabase rcdb, CentralVersionDatabase cvdb,
            ExpulsionDatabase exdb, ContentFetchQueueDatabase cfqdb, MapSIndex2Store sidx2s)
    {
        _ds = ds;
        _ps = ps;
        _sidx2sid = sidx2sid;
        _om = om;
        _od = od;
        _oc = oc;
        _os = os;
        _rldb = rldb;
        _mcdb = mcdb;
        _ccdb = ccdb;
        _rcdb = rcdb;
        _cvdb = cvdb;
        _polaris = polaris;
        _exdb = exdb;
        _cfqdb = cfqdb;
        _sidx2s = sidx2s;
    }

    /**
     * Important:
     *
     * To prevent the users from creating nested shares by moving anchors inside non-root stores
     * we special-case the migration of anchors: they are converted back to regular folders
     *
     * NB: This is the same behavior as Dropbox so ww is super happy
     */
    private static Type migratedType(Type t)
    {
        return t == Type.ANCHOR ? Type.DIR : t;
    }

    /**
     * When a shared folder is converted back to a regular folder we need to generate a new OID
     * to break the link between the two objects. This is necessary to avoid all sorts of nasty
     * coupling. For instance, reusing the original folder OID would prevent the new folder from
     * being re-shared later.
     */
    private static OID migratedOID(OID oid)
    {
        return oid.isAnchor() ? new OID(UniqueID.generate()) : oid;
    }

    /**
     * This method either moves objects within the same store, or across stores via migration,
     * depending on whether the old sidx is the same as the new one.
     *
     * @return the SOID of the object after the move. This new SOID may be different from
     * the parameter {@code soid} if migration occurs.
     *
     * Note: This is a method operate at the topmost level. Putting it in ObjectMover would
     * introduce a circular dependency, which is why it lives in ImmigrantCreator instead.
     * This area of the code would benefit from a good helping of refactoring but now is not
     * the time...
     */
    public SOID move_(SOID soid, SOID soidToParent, String toName, PhysicalOp op, Trans t)
            throws Exception
    {
        checkState(op != PhysicalOp.NOP);
        if (soidToParent.sidx().equals(soid.sidx())) {
            _om.moveInSameStore_(soid, soidToParent.oid(), toName, op, true, t);
            return soid;
        } else if (_polaris.get()) {
            OA oa = _ds.getOA_(soid);
            ResolvedPath p = _ds.resolve_(oa);
            OA oaParentTo = _ds.getOA_(soidToParent);
            ResolvedPath dest = _ds.resolve_(oaParentTo).join(soid, toName);
            if (!oa.isExpelled() && !oaParentTo.isExpelled()) {
                if (oa.isFile()) {
                    for (KIndex kidx : oa.cas().keySet()) {
                        _ps.newFile_(p, kidx).move_(dest, kidx, op, t);
                    }
                } else {
                    _ps.newFolder_(p).move_(dest, op, t);
                }
                op = PhysicalOp.MAP;
            }
            return createImmigrantRecursively_(p.parent(), soid, soidToParent,
                    toName, op, null, t);
        } else {
            return createLegacyImmigrantRecursively_(_ds.resolve_(soid).parent(), soid, soidToParent,
                    toName, op, t);
        }
    }

    /**
     * Here be dragons
     *
     * See {@link com.aerofs.daemon.core.polaris.fetch.ApplyChangeImpl#share_}
     */
    public SOID createImmigrantRecursively_(ResolvedPath pathFromParent, final SOID soidFromRoot,
                                            final SOID soidToRootParent, final String toRootName,
                                            final PhysicalOp op, @Nullable RemoteTreeCache cache,
                                            final Trans t)
            throws Exception {
        // thou shalt not move ROOT or TRASH folder
        final SIndex sidxFrom = soidFromRoot.sidx();

        checkArgument(!soidFromRoot.oid().isRoot());
        checkArgument(!soidFromRoot.oid().isTrash());
        // ensure moving across store boundary
        checkArgument(!sidxFrom.equals(soidToRootParent.sidx()));

        SIndex sidxTo = soidToRootParent.sidx();
        MigratedPath pathParent = new MigratedPath(
                pathFromParent,
                _ds.resolve_(soidToRootParent));

        l.info("{} -> {}", pathParent.from, pathParent.to);

        _ds.walk_(soidFromRoot, pathParent, new IObjectWalker<MigratedPath>() {
            @Override
            public MigratedPath prefixWalk_(MigratedPath pathParent, OA oaFrom)
                    throws Exception {
                // do not walk trash
                // override sidxFrom in case of nested sharing
                SIndex sidxFrom = oaFrom.soid().sidx();
                OID oidFrom = oaFrom.soid().oid();

                // when walking across store boundary (i.e through an anchor), we do not need
                // to re-create the root dir in the destination, however we need to make sure
                // any physical trace of the former anchor disappears
                if (oidFrom.isRoot()) return pathParent;
                // no need to walk through the trash of any nested share being migrated
                if (oidFrom.isTrash()) return null;

                SOID soidToParent = oidFrom.equals(soidFromRoot.oid())
                        ? soidToRootParent : pathParent.to.soid();

                // NB: reuse OID iff the object has not inadvertently crossed share boundary
                boolean reuseId = !oaFrom.isAnchor() && canReuseId(oidFrom);

                SOID soidTo = new SOID(sidxTo, reuseId ? oidFrom : OID.generate());
                String name = soidFromRoot.equals(oaFrom.soid()) ? toRootName : oaFrom.name();

                l.info("{} {} {} {}", oaFrom.soid(), soidToParent, soidTo, name);

                // make sure the physical file reflect the migrated SOID before any MAP operation
                ResolvedPath pathFrom = pathParent.from.join(oaFrom.soid(), oaFrom.name());
                if (oaFrom.isFile()) {
                    for (KIndex kidx : oaFrom.cas().keySet()) {
                        _ps.newFile_(pathFrom, kidx).updateSOID_(soidTo, t);
                    }
                } else {
                    _ps.newFolder_(pathFrom).updateSOID_(soidTo, t);
                }

                Type typeTo = migratedType(oaFrom.type());

                // NB: create a MetaChange in the db iff the OID is not reused
                //
                // OIDs are only reused when a strict set of conditions are met. In particular this
                // only happens when sharing, not in more general cross-store moves, when the object
                // is known to be in the shared subtree both on polaris and locally. In this case,
                // local meta changes in the source store within the shared subtree are copied in
                // the new store in ApplyChangeImpl
                //
                // When new OIDs are generated, a local change needs to be generated to ensure the
                // OID propagates to polaris and from there to other peers.
                boolean metaChange = !reuseId || _rldb.getParent_(sidxFrom, oidFrom) == null;
                _oc.createOA_(typeTo, soidTo, soidToParent.oid(), name, metaChange, t);

                // preserve explicit expulsion state
                if (oaFrom.isSelfExpelled()) {
                    _ds.setExpelled_(soidTo, true, t);
                    _exdb.insertExpelledObject_(soidTo, t);
                    _exdb.deleteExpelledObject_(oaFrom.soid(), t);
                }

                MigratedPath p = pathParent.join(oaFrom, soidTo, name);

                boolean isExpelled = _ds.getOA_(soidTo).isExpelled();

                if (reuseId && preserveVersions_(sidxFrom, oidFrom, oaFrom.type(), sidxTo, t) && !isExpelled) {
                    _cfqdb.insert_(sidxTo, oidFrom, t);
                }

                // no fiddling with physical objects if the target is expelled...
                if (isExpelled) {
                    return p;
                }

                if (oaFrom.isFile()) {
                    if (oaFrom.caMasterNullable() != null
                            && (!reuseId || _ccdb.hasChange_(sidxFrom, oidFrom))) {
                        _ccdb.insertChange_(soidTo.sidx(), soidTo.oid(), t);
                    }

                    l.info("{} {} {}", soidTo, oaFrom);

                    for (Entry<KIndex, CA> e : oaFrom.cas().entrySet()) {
                        KIndex kidx = e.getKey();
                        CA caFrom = e.getValue();
                        ContentHash hFrom = _ds.getCAHash_(new SOKID(oaFrom.soid(), kidx));
                        _ds.createCA_(soidTo, kidx, t);
                        _ds.setCA_(new SOKID(soidTo, kidx), caFrom.length(), caFrom.mtime(), hFrom, t);
                        _ps.newFile_(p.to, kidx).create_(PhysicalOp.MAP, t);
                    }
                } else {
                    IPhysicalFolder pf = _ps.newFolder_(p.to);
                    pf.create_(op, t);

                    // remove the tag file from the destination to gracefully handle both MAP and APPLY
                    if (oaFrom.isAnchor()) {
                        pf.demoteToRegularFolder_(SID.anchorOID2storeSID(oidFrom), op, t);
                    }
                }

                return p;
            }

            private boolean canReuseId(OID oid) throws SQLException {
                return cache != null &&
                        (cache.isInSharedTree(oid) || _rldb.getParent_(sidxFrom, oid) == null);
            }

            @Override
            public void postfixWalk_(MigratedPath pathParent, OA oaFrom)
                    throws Exception {
                // there shall not be duplicate OIDs
                // polaris should not allow nested sharing but there's always the possibility of
                // a local change moving an anchor underneath a tree being migrated
                OID oidFrom = oaFrom.soid().oid();
                if (oidFrom.isRoot() || oidFrom.isTrash()) return;

                boolean reuseId = !oaFrom.isAnchor() && canReuseId(oidFrom);
                // no need to publish local meta changes in old store
                // NB: do not immediately delete meta changes for reused OIDs
                // as the remote tree walk done after the local tree walk will take care of it
                // FIXME(phoenix): indiscriminate deletion may not be 100% safe...
                if (!reuseId || _rldb.getParent_(sidxFrom, oidFrom) == null) {
                    _mcdb.deleteChanges_(sidxFrom, oidFrom, t);
                }
                _ccdb.deleteChange_(sidxFrom, oidFrom, t);
                if (reuseId) {
                    if (oaFrom.isDir()) {
                        // NB: careful about listing children
                        // One does not simply keep multiple ResultSet open for the same prepared
                        // statement. Because of an optimization in DirectoryService#walk_ files
                        // are walked immediately and folders are placed into a list to be walked
                        // later. Calling listChildren for a file would reset the iterator in
                        // DirectoryService#walk_ and cause the migration to skip children, in turn
                        // causing this very assertion to fail
                        try (IDBIterator<OID> it = _ds.listChildren_(oaFrom.soid())) {
                            checkState(!it.next_());
                        }
                    }
                    _os.deleteOA_(oaFrom.soid(), t);
                } else {
                    // avoid deleting object if its parent will be deleted
                    // NB: this cascades up to result in a single deletion at the root of a subtree
                    // instead of deleting every node in the subtree
                    if (oidFrom.equals(soidFromRoot.oid()) || canReuseId(oaFrom.parent())) {
                        _od.delete_(oaFrom.soid(), op != PhysicalOp.APPLY ? PhysicalOp.NOP : op, t);
                    }
                }
            }
        });

        // make sure any local changes moved to the new store get submitted
        PolarisStore s = (PolarisStore)_sidx2s.get_(sidxTo);
        s.contentSubmitter().startOnCommit_(t);
        s.metaSubmitter().startOnCommit_(t);

        return new SOID(soidToRootParent.sidx(), soidFromRoot.oid());
    }

    public boolean preserveVersions_(SIndex sidxFrom, OID oidFrom, OA.Type type, SIndex sidxTo, Trans t)
            throws SQLException {
        // preserve version to avoid false conflicts across SHARE boundary
        Long v = _cvdb.getVersion_(sidxFrom, oidFrom);
        if (v != null) {
            _cvdb.deleteVersion_(sidxFrom, oidFrom, t);
            if (type == Type.FILE) _cvdb.setVersion_(sidxTo, oidFrom, v, t);
        }

        // preserve remote content info when preserving OID
        boolean shouldCollect = false;
        try (IDBIterator<RemoteContent> it = _rcdb.list_(sidxFrom, oidFrom)) {
            while (it.next_()) {
                RemoteContent rc = it.get_();
                shouldCollect = v == null || v < rc.version;
                _rcdb.insert_(sidxTo, oidFrom, rc.version, rc.originator, rc.hash, rc.length, t);
            }
        }
        _rcdb.deleteUpToVersion_(sidxFrom, oidFrom, Long.MAX_VALUE, t);
        return shouldCollect;
    }

    static class MigratedPath
    {
        public final ResolvedPath from;
        public final ResolvedPath to;

        MigratedPath(ResolvedPath from, ResolvedPath to)
        {
            this.from = from;
            this.to = to;
        }

        MigratedPath join(OA from, SOID to, String name)
        {
            return new MigratedPath(this.from.join(from.soid(), from.name()), this.to.join(to, name));
        }
    }

    /**
     * Recursively migrate the object corresponding to {@code soidFromRoot} to
     * under {@code soidToRootParent}.
     *
     * This method assumes that permissions have been checked.
     *
     * @param soidFromRoot the SOID of the root object to be migrated
     * @param soidToRootParent the SOID of the parent to which the root object will be migrated
     * @return the new SOID of the root object
     */
    public SOID createLegacyImmigrantRecursively_(ResolvedPath pathFromParent, final SOID soidFromRoot,
                                                  final SOID soidToRootParent, final String toRootName, final PhysicalOp op, final Trans t)
            throws Exception
    {
        // thou shalt not move ROOT or TRASH folder
        checkArgument(!soidFromRoot.oid().isRoot());
        checkArgument(!soidFromRoot.oid().isTrash());
        // ensure moving across store boundary
        checkArgument(!soidFromRoot.sidx().equals(soidToRootParent.sidx()));

        MigratedPath pathParent = new MigratedPath(
                pathFromParent,
                _ds.resolve_(soidToRootParent).substituteLastSOID(soidToRootParent));

        _ds.walk_(soidFromRoot, pathParent, new IObjectWalker<MigratedPath>() {
            @Override
            public MigratedPath prefixWalk_(MigratedPath pathParent, OA oaFrom)
                    throws Exception
            {
                // do not walk trash
                if (oaFrom.soid().oid().isTrash()) return null;

                // when walking across store boundary (i.e through an anchor), we do not need
                // to re-create the root dir in the destination, however we need to make sure
                // any physical trace of the former anchor disappears
                if (oaFrom.soid().oid().isRoot()) return pathParent;

                SOID soidToParent = pathParent.to.isEmpty()
                        ? soidToRootParent : pathParent.to.soid();
                SOID soidTo = new SOID(soidToParent.sidx(), migratedOID(oaFrom.soid().oid()));
                String name = soidFromRoot.equals(oaFrom.soid()) ? toRootName : oaFrom.name();

                // make sure the physical file reflect the migrated SOID before any MAP operation
                if (op == PhysicalOp.MAP) {
                    ResolvedPath pathFrom = pathParent.from.join(oaFrom.soid(), oaFrom.name());
                    if (oaFrom.isFile()) {
                        for (KIndex kidx : oaFrom.cas().keySet()) {
                            _ps.newFile_(pathFrom, kidx).updateSOID_(soidTo, t);
                        }
                    } else {
                        _ps.newFolder_(pathFrom).updateSOID_(soidTo, t);
                    }
                }

                OA oaToExisting = _ds.getOANullable_(soidTo);
                Type typeTo = migratedType(oaFrom.type());

                if (oaToExisting == null) {
                    _oc.createImmigrantMeta_(typeTo, oaFrom.soid(), soidTo, soidToParent.oid(),
                            name, op, true, t);
                } else {
                    // Comment (B)
                    //
                    // It's an invariant that at any given time, among all the
                    // objects sharing the same OID in the local system, at
                    // most one of them is admitted, this is guaranteed by the
                    // implementation. See the invariant in AdmittedObjectLocator.
                    checkState(typeTo == oaToExisting.type());
                    checkState(oaFrom.isExpelled() || oaToExisting.isExpelled(),
                            oaFrom + " " + oaToExisting);
                    _om.moveInSameStore_(soidTo, soidToParent.oid(), name, op, true, t);
                }

                // remove the tag file from the destination to gracefully handle both MAP and APPLY
                if (oaFrom.isAnchor()) {
                    // the IPhysicalFolder needs to be created with the anchor OID
                    // but we cannot simply reuse that of the old OA because it probably does not
                    // point to the correct path...
                    _ps.newFolder_(_ds.resolve_(soidTo))
                            .demoteToRegularFolder_(SID.anchorOID2storeSID(oaFrom.soid().oid()), op, t);
                }

                return pathParent.join(oaFrom, soidTo, name);
            }

            @Nullable SID _sid;

            @Override
            public void postfixWalk_(MigratedPath pathParent, OA oaFrom)
                    throws Exception
            {
                if (oaFrom.soid().oid().isRoot() || oaFrom.soid().oid().isTrash()) return;

                if (_sid == null) {
                    _sid = pathParent.to.isEmpty()
                            ? pathParent.to.sid()
                            : _sidx2sid.get_(pathParent.to.soid().sidx());
                }

                // The use and abuse of PhysicalOp in Aliasing and migration has been a major
                // source of grief when doing changes in the core. We really need to come up
                // with better semantics.
                // In this case we should not use MAP when deleting because this would try to
                // delete NROs/conflicts that don't actually exist as they've been renamed to
                // reflect the SOID change.
                PhysicalOp realOp = op == PhysicalOp.MAP ? PhysicalOp.NOP : op;

                if (oaFrom.isAnchor()) {
                    // NB: to properly leave the store we must not keep track of the emigration
                    _od.delete_(oaFrom.soid(), realOp, t);
                } else {
                    // NOP is used for files as their content has already been moved as required
                    // in the prefix walk and we don't want to mistakenly delete them when the
                    // migration doesn't change the physical path (i.e. sharing)
                    if (oaFrom.isFile()) realOp = PhysicalOp.NOP;
                    _od.deleteAndEmigrate_(oaFrom.soid(), realOp, _sid, t);
                }
            }
        });

        return new SOID(soidToRootParent.sidx(), soidFromRoot.oid());
    }
}
