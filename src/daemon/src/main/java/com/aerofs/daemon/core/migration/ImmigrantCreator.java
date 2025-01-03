package com.aerofs.daemon.core.migration;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.daemon.core.ds.*;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.polaris.PolarisAsyncClient;
import com.aerofs.daemon.core.polaris.api.LocalChange;
import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.daemon.core.polaris.db.*;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase.RemoteContent;
import com.aerofs.daemon.core.store.DaemonPolarisStore;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.lib.db.ExpulsionDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import com.aerofs.lib.sched.ExponentialRetry.ExRetryLater;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;

import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import java.sql.SQLException;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

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
    private final ObjectMover _om;
    private final ObjectDeleter _od;
    private final ObjectSurgeon _os;
    private final IMapSIndex2SID _sidx2sid;
    private final RemoteLinkDatabase _rldb;
    private final CentralVersionDatabase _cvdb;
    private final MetaChangesDatabase  _mcdb;
    private final ContentChangesDatabase _ccdb;
    private final RemoteContentDatabase _rcdb;
    private final ExpulsionDatabase _exdb;
    private final ContentFetchQueueWrapper _cfqw;
    private final MapSIndex2Store _sidx2s;
    private final PolarisAsyncClient _client;

    private final Executor _sameThread = MoreExecutors.sameThreadExecutor();

    @Inject
    public ImmigrantCreator(DirectoryService ds, IPhysicalStorage ps, IMapSIndex2SID sidx2sid,
            ObjectMover om, ObjectDeleter od, ObjectSurgeon os,
            RemoteLinkDatabase rldb, MetaChangesDatabase mcdb,
            ContentChangesDatabase ccdb, RemoteContentDatabase rcdb, CentralVersionDatabase cvdb,
            ExpulsionDatabase exdb, ContentFetchQueueWrapper cfqw, MapSIndex2Store sidx2s,
            PolarisAsyncClient.Factory clientFactory)
    {
        _ds = ds;
        _ps = ps;
        _sidx2sid = sidx2sid;
        _om = om;
        _od = od;
        _os = os;
        _rldb = rldb;
        _mcdb = mcdb;
        _ccdb = ccdb;
        _rcdb = rcdb;
        _cvdb = cvdb;
        _exdb = exdb;
        _cfqw = cfqw;
        _sidx2s = sidx2s;
        // TODO: use same instance as HdShareFolder?
        _client = clientFactory.create();
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

    private UniqueID rootOID2SID(SIndex sidx, OID oid)
    {
        return oid.isRoot() ? _sidx2sid.get_(sidx) : oid;
    }

    public static class ExMigrationDelayed extends ExRetryLater {
        private static final long serialVersionUID = 0L;
        public ExMigrationDelayed(String msg) {
            super(msg);
        }
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
        }

        // NB: this is really gross, especially considering that this can be triggered by
        // a filesystem notification
        // Ideally cross-store moves would be submitted asynchronously, like regular meta
        // changes. However the current state of things makes that extremely hard to do safely.
        // Better solutions would require either dropping support for legacy p2p entirely or
        // maintaining two completely different code bases with largely incompatible DB schemas.
        // Neither of these is currently acceptable.
        // Hopefully offline cross-store moves are rare enough that the synchronous polaris
        // request doesn't cause too much trouble
        // TODO: revisit this once p2p meta sync is burned

        // Wait until we the object being migrated is in the remote tree
        //  - if it's not know to polaris, the transform will be rejected with a 404
        //  - otherwise we want to avoid races where we migrate an object before receiving
        //    the ack of its INSERT transform and end up restoring the object incorrectly,
        //    which in turn can result in the object being incorrectly deleted if it is migrated
        //    back and forth between two stores.
        if (_rldb.getParent_(soid.sidx(), soid.oid()) == null) {
            throw new ExMigrationDelayed("unresolved src meta: " + soid);
        }
        if (!soidToParent.oid().isRoot() && _rldb.getParent_(soidToParent.sidx(), soidToParent.oid()) == null) {
            throw new ExMigrationDelayed("unresolved dst meta: " + soidToParent);
        }

        OA oa = _ds.getOA_(soid);
        LocalChange c = new LocalChange();
        c.type = LocalChange.Type.MOVE_CHILD;
        c.child = soid.oid().toStringFormal();
        c.newChildName = toName;
        c.newParent = rootOID2SID(soidToParent.sidx(), soidToParent.oid()).toStringFormal();
        SettableFuture<UniqueID> f = SettableFuture.create();
        _client.post("/objects/" + rootOID2SID(soid.sidx(), oa.parent()).toStringFormal(), c,
                new AsyncTaskCallback() {
                    @Override
                    public void onSuccess_(boolean hasMore) {}

                    @Override
                    public void onFailure_(Throwable t) {
                        f.setException(t);
                    }
                }, r -> handle_(f, r), _sameThread);
        // wait for polaris response
        try {
            f.get();
        } catch (ExecutionException e) {
            l.info("", e);
            throw new ExMigrationDelayed(e.getMessage());
        }

        // polaris accepted the move, proceed with local migration
        // NB: it is unfortunate but necessary to do a local migration before receiving the
        // migrated transforms from polaris as the caller expects the tree to be migrated
        // immediately and there is no tractable way to change that since this method is called
        // to adjust the logical tree after a filesystem change.
        // This will result in conflicting OIDs which will eventually be resolved by aliasing
        // or renaming. Some duplication may arise if the local tree diverged from the remote
        // tree. This is suboptimal but about as good as can be done at this point.
        // Slightly better edge-case behavior might be achievable through extensive change in
        // the logical object tree but the amount of work required would be significant and the
        // benefits marginal so it is left as an exercise for the future maintainer.
        // TODO: revisit this once p2p meta sync is burned
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
    }

    private boolean handle_(SettableFuture<UniqueID> f, HttpResponse r) throws Exception {
        String content = r.getContent().toString(BaseUtil.CHARSET_UTF);
        if (!r.getStatus().equals(HttpResponseStatus.OK)) {
            l.info("polaris error {}\n{}", r.getStatus(), content);
            // TODO: handle lack of WRITE access to source or dest store
            if (r.getStatus().getCode() >= 500) {
                throw new ExRetryLater(r.getStatus().getReasonPhrase());
            }
            throw new ExProtocolError(r.getStatus().getReasonPhrase());
        }
        // TODO
        f.set(null);
        return false;
    }

    private boolean canReuseId(SIndex sidxFrom, OID oid, RemoteTreeCache cache) throws SQLException {
        return cache != null &&
                (cache.isInSharedTree(oid) || _rldb.getParent_(sidxFrom, oid) == null);
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

        OA oaFromRoot = _ds.getOA_(soidFromRoot);
        OID oidToRoot = !oaFromRoot.isAnchor() && canReuseId(sidxFrom, soidFromRoot.oid(), cache)
                ? soidFromRoot.oid() : OID.generate();

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
                boolean reuseId = !oaFrom.isAnchor() && canReuseId(sidxFrom, oidFrom, cache);

                SOID soidTo;
                String name;
                if (soidFromRoot.equals(oaFrom.soid())) {
                    soidTo = new SOID(sidxTo, oidToRoot);
                    name = toRootName;
                } else {
                    soidTo = new SOID(sidxTo, reuseId ? oidFrom : OID.generate());
                    name = oaFrom.name();
                }
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
                _ds.createOA_(typeTo, soidTo.sidx(), soidTo.oid(), soidToParent.oid(), name, t);
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
                if (!reuseId || _rldb.getParent_(sidxFrom, oidFrom) == null) {
                    _mcdb.insertChange_(sidxTo, soidTo.oid(), soidToParent.oid(), name,
                            reuseId || oaFrom.isAnchor() ? null : oidFrom, t);
                }

                // preserve explicit expulsion state
                if (oaFrom.isSelfExpelled()) {
                    _ds.setExpelled_(soidTo, true, t);
                    _exdb.insertExpelledObject_(soidTo, t);
                    _exdb.deleteExpelledObject_(oaFrom.soid(), t);
                }

                MigratedPath p = pathParent.join(oaFrom, soidTo, name);

                boolean isExpelled = _ds.getOA_(soidTo).isExpelled();

                if (reuseId && preserveVersions_(sidxFrom, oidFrom, oaFrom.type(), sidxTo, t) && !isExpelled) {
                    _cfqw.insert_(sidxTo, oidFrom, t);
                }

                // no fiddling with physical objects if the target is expelled...
                if (isExpelled) {
                    return p;
                }

                if (oaFrom.isFile()) {
                    // preserve local content changes
                    // NB: we do NOT need to advertise content if we don't have local changes as
                    // polaris will take care of migrating the latest content state of every file
                    // during any cross-store move (SHARE or MOVE_CHILD across store boundary)
                    // Advertising content when there is no change would create false conflicts
                    // if some devices are not fully in sync at the time of the migration.
                    if (oaFrom.caMasterNullable() != null
                            && _ccdb.hasChange_(sidxFrom, oidFrom)) {
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

            @Override
            public void postfixWalk_(MigratedPath pathParent, OA oaFrom)
                    throws Exception {
                // there shall not be duplicate OIDs
                // polaris should not allow nested sharing but there's always the possibility of
                // a local change moving an anchor underneath a tree being migrated
                OID oidFrom = oaFrom.soid().oid();
                if (oidFrom.isRoot() || oidFrom.isTrash()) return;

                boolean reuseId = !oaFrom.isAnchor() && canReuseId(sidxFrom, oidFrom, cache);
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
                    if (oidFrom.equals(soidFromRoot.oid()) || canReuseId(sidxFrom, oaFrom.parent(), cache)) {
                        _od.delete_(oaFrom.soid(), op != PhysicalOp.APPLY ? PhysicalOp.NOP : op, t);
                    }
                }
            }
        });

        // make sure any local changes moved to the new store get submitted
        DaemonPolarisStore s = (DaemonPolarisStore)_sidx2s.get_(sidxTo);
        s.contentSubmitter().startOnCommit_(t);
        s.metaSubmitter().startOnCommit_(t);

        return new SOID(soidToRootParent.sidx(), oidToRoot);
    }

    public boolean preserveVersions_(SIndex sidxFrom, OID oidFrom, OA.Type type, SIndex sidxTo, Trans t)
            throws SQLException {
        // preserve version to avoid false conflicts across SHARE boundary
        // FIXME: version may reflect conflict branch state instead of MASTER
        Long v = _cvdb.getVersion_(sidxFrom, oidFrom);
        if (v != null) {
            _cvdb.deleteVersion_(sidxFrom, oidFrom, t);
            if (type == Type.FILE) {
                // NB: this does NOT go through PolarisContentVersionControl
                // because at this point we have no idea what the logical timestamp for the polaris
                // transform will end up being so we cannot safely update bloom filters
                // instead, ApplyChangeImpl will be responsible for updating BF and content change
                // epoch when it receives the "obsolete" UPDATE_CONTENT transform
                _cvdb.setVersion_(sidxTo, oidFrom, v, t);
            }
        }
        if (type != Type.FILE) return false;

        // preserve remote content info when preserving OID
        boolean shouldCollect = v == null;
        try (IDBIterator<RemoteContent> it = _rcdb.list_(sidxFrom, oidFrom)) {
            while (it.next_()) {
                RemoteContent rc = it.get_();
                shouldCollect = shouldCollect || v < rc.version;
                _rcdb.insert_(sidxTo, oidFrom, rc.version, rc.originator, rc.hash, rc.length, t);
            }
        }
        _rcdb.deleteUpToVersion_(sidxFrom, oidFrom, Long.MAX_VALUE, t);

        l.debug("preserved version {} {} -> {} {} {}", oidFrom, sidxFrom, sidxTo, v, shouldCollect);
        return shouldCollect;
    }

    public static class MigratedPath
    {
        public final ResolvedPath from;
        public final ResolvedPath to;

        public MigratedPath(ResolvedPath from, ResolvedPath to)
        {
            this.from = from;
            this.to = to;
        }

        public MigratedPath join(OA from, SOID to, String name)
        {
            return new MigratedPath(this.from.join(from.soid(), from.name()), this.to.join(to, name));
        }
    }
}
