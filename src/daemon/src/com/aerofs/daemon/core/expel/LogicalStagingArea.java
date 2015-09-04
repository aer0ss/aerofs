/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.expel;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ContentVersionControl;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.LogicalStagingAreaDatabase.StagedFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.protocol.PrefixVersionControl;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * To support atomic deletion of large objects tree without causing a DoS, we introduce the concept
 * of a staging area.
 *
 * Immediately deleting objects in a single massive transaction, is not only slow but gets slower
 * and slower as the Write Ahead Log used by sqlite grows. It also leads to OOMs as the TransLocal
 * data structures grow too large.
 *
 * Instead we adopt a model in which a first small transaction does the bare minimum required to
 * mark the tree as deleted and ensure that externally observable behavior is preserved. After
 * that, the tree can be incrementally cleaned up in a series of small transactions. Besides
 * preventing OOMs and unbounded WAL growth, this ensures progress can be made in the face of
 * crashes.
 *
 * The staging area is backed by a new DB table that maps SOID to an optional "history path", i.e.
 * the path at which the object resided at the time it was staged. An empty path (i.e. SID but
 * no path components) is used to indicate that sync history need not be preserved.
 *
 * When an object subtree is expelled or deleted, its root is added to the staging area and cleanup
 * is started as needed. Cleanup happen in separate core events, after the initial transaction
 * marking the subtree as staged is committed. Cleanup happens breath-first. An object is taken
 * from the staging area db, and all its children are processed in no particular order.
 *
 * In some cases, the asynchronous cleanup will not be fast enough. For instance a user might
 * expel a large object tree and immediately re-admit it. When that happens it is necessary to
 * perform an atomic, synchronous cleanup of the subtree that is being pulled out of the staging
 * area. This may cause one of the large transactions that the staging area was built to aovid,
 * which is unfortunate but unavoidable. Fortunately, even if the synchronous cleanup fails, the
 * asynchronous cleanup will keep making steady progress and the synchronous cleanup will eventually
 * succeed if it is retried.
 *
 * This class is also responsible for the execution of deferred store deletion operators, which
 * happens after the last object from an expelled store is cleaned up.
 * See {@link com.aerofs.daemon.core.store.StoreDeletionOperators} for more details.
 *
 * See also docs/design/scalable_deletion.md for the original design proposal.
 */
public class LogicalStagingArea extends AbstractLogicalStagingArea
{
    private final static Logger l = Loggers.getLogger(LogicalStagingArea.class);

    private final DirectoryService _ds;
    private final ContentVersionControl _cvc;
    private final PrefixVersionControl _pvc;

    @Inject
    public LogicalStagingArea(DirectoryService ds, IPhysicalStorage ps,
            ContentVersionControl cvc, PrefixVersionControl pvc, LogicalStagingAreaDatabase sadb,
            CoreScheduler sched, IMapSIndex2SID sidx2sid, StoreDeletionOperators sdo,
            TransManager tm)
    {
        super(sidx2sid, sadb, sched, sdo, tm, ps);
        _ds = ds;
        _cvc = cvc;
        _pvc = pvc;
    }

    /**
     * NB: we do not need to pay attention to the physical op here:
     *
     *   - Scrubbing only affects SOID-indexed physical objects.
     *
     *   - NOP deletion only occurs during migration, in which case the physical objects
     *     will have already been updated by updateSOID_ before the deletion occurs
     *
     *   - MAP deletion only occurs on linked storage and behaves like APPLY for
     *     SOID-indexed physical objects
     *
     * @param soid root of the object subtree to be cleaned up
     * @param pOld path to {@paramref soid} at the time of deletion/expulsion
     */
    @Override
    public void stageCleanup_(SOID soid, ResolvedPath pOld, Trans t)
            throws Exception
    {
        Path historyPath = _ps.isDiscardingRevForTrans_(t) ? Path.root(pOld.sid()) : pOld;
        stageCleanup_(soid, historyPath, t);
    }

    /**
     * Ensures that given object is clean.
     *
     * NB: this is NOT recursive. It is the caller's responsibility to walk the tree if a
     * recursive immediate cleanup is needed.
     */
    public void ensureClean_(ResolvedPath pathOld, Trans t) throws Exception
    {
        SOID soid = pathOld.soid();
        @Nullable Path historyPath = getHistoryPath_(pathOld);
        l.debug("ensure clean {}: {}", soid, historyPath);

        if (historyPath == null) return;

        cleanupObject_(_ds.getOA_(soid), historyPath, t);
    }

    /**
     * @pre both the old and new path are expelled
     *
     * If a path was staged, ensure that moving it around will not mess with cleanup (move under
     * clean expelled parent) or history
     */
    public void preserveStaging_(ResolvedPath pathOld, Trans t)
            throws SQLException, IOException
    {
        SOID soid = pathOld.soid();
        if (isStaged_(soid)) return;

        @Nullable Path historyPath = getHistoryPath_(pathOld);
        if (historyPath == null) return;

        stageCleanup_(soid, historyPath, t);
    }

    /**
     * Recursively cleanup a subtree in the current transaction
     *
     * @pre the subtree is being readmitted
     */
    @Override
    protected void immediateCleanup_(final SOID soidRoot, Path historyPath, final Trans t)
            throws Exception
    {
        l.info("immediate cleanup {} {}", soidRoot, historyPath);
        _ds.walk_(soidRoot, historyPath, new IObjectWalker<Path>() {
            @Override
            public @Nullable Path prefixWalk_(Path pOldParent, OA oa)
                    throws SQLException, IOException
            {
                boolean isRoot = soidRoot.equals(oa.soid());
                boolean oldExpelled = oa.isSelfExpelled();
                checkState(!(isRoot && oldExpelled), "%s", oa.soid());

                Path pathOld = isRoot || pOldParent.isEmpty()
                        ? pOldParent
                        : pOldParent.append(oa.name());

                // check for and honor divergent history path for subtree
                if (!isRoot) {
                    Path h = _sadb.historyPath_(oa.soid());
                    if (h != null) {
                        pathOld = h;
                        _sadb.removeEntry_(oa.soid(), t);
                    }
                }

                if (oa.isDir() && oldExpelled) return null;

                cleanupObject_(oa, pathOld, t);

                return oa.isDir() ? pathOld : null;
            }

            @Override
            public void postfixWalk_(Path pOldParent, OA oa)
            { }
        });

        finalize_(soidRoot, historyPath.sid(), t);
    }

    /**
     * Determine the history path of an object.
     *
     * If the object is not explicitly staged, recursively check ancestors for explicit staging,
     * in bottom-up fashion. Take the history path of the first (innermost) ancestor that is
     * explicitly staged and append the relative path between this ancestor and the object of
     * interest to form the actual history path.
     *
     * NB: this methods performs NO metadatabase lookups, instead relying only on the input Path.
     *
     * @return
     *      - null if the object is not staged (explicitly or implicitly)
     *      - empty if the innermost staged folder is marked to not keep history
     *      - otherwise, join(innermost history path, child path)
     */
    private Path getHistoryPath_(ResolvedPath p) throws SQLException
    {
        for (int i = p.soids.size() - 1; i >= 0; --i) {
            SOID soid = p.soids.get(i);
            Path historyPath = _sadb.historyPath_(soid);
            if (historyPath != null) {
                if (historyPath.isEmpty()) return historyPath;
                return historyPath.append(p.elements(), i + 1, p.soids.size() - (i + 1));
            }
        }
        return null;
    }

    /**
     * Stage an expelled subtree
     *
     * Actual cleanup may be deferred to separate transactions scheduled in later core events
     *
     * @pre the top-level physical object should have been deleted (recursively if applicable)
     */
    private void stageCleanup_(SOID soid, @Nonnull Path historyPath, Trans t)
            throws SQLException, IOException
    {
        OA oa = _ds.getOANullableNoFilter_(soid);
        if (oa == null) return;

        if (oa.fidNoExpulsionCheck() != null) {
            _ds.unsetFID_(soid, t);
        }
        // anchors need no cleanup (see AdmittedToExpelledAdjuster)
        if (oa.isAnchor()) return;

        if (oa.isFile()) {
            cleanupFile_(oa, historyPath, t);
            return;
        }

        checkState(oa.isDir());
        if (soid.oid().isRoot()) {
            checkState(isStoreStaged_(soid.sidx()));
            // finalize store cleanup immediately for external roots and empty stores
            if (!_ps.shouldScrub_(_sidx2sid.getAbsent_(soid.sidx())) || !_ds.hasChildren_(soid)) {
                finalizeStoreCleanup_(soid.sidx(), historyPath.sid(), t);
                return;
            }
        } else if (!_ds.hasChildren_(soid)) {
            return;
        }

        l.info("sched dir cleanup {} {}", soid, historyPath);
        _sadb.addEntry_(soid, historyPath, t);
        t.addListener_(new AbstractTransListener()
        {
            @Override
            public void committed_()
            {
                _sas.schedule_();
            }
        });
    }

    @Override
    public boolean process_() throws Exception
    {
        IDBIterator<StagedFolder> it = _sadb.listEntries_();
        try {
            ElapsedTimer timer = new ElapsedTimer();
            while (it.next_()) {
                processStagedFolder_(it.get_());
                // if cleanup is taking too long, reschedule to
                // allow other core threads to make progress
                if (timer.elapsed() > RESCHEDULE_THRESHOLD) return true;
            }
        } finally {
            it.close_();
        }
        l.info("logical staging area empty");
        return false;
    }

    @Override
    protected void processStagedFolder_(StagedFolder f) throws SQLException, IOException
    {
        l.info("logical cleanup {} {}", f.soid, f.historyPath);
        Trans t = _tm.begin_();
        try {
            try (IDBIterator<OID> it = _ds.listChildren_(f.soid)) {
                long n = 0;
                while (it.next_()) {
                    OA oa = _ds.getOANullableNoFilter_(new SOID(f.soid.sidx(), it.get_()));
                    checkNotNull(oa);

                    if (oa.isSelfExpelled() || isStaged_(oa.soid())) continue;

                    Path historyPath = f.historyPath.isEmpty()
                            ? f.historyPath
                            : f.historyPath.append(oa.name());

                    if (cleanupObject_(oa, historyPath, t)) {
                        _sadb.addEntry_(oa.soid(), historyPath, t);
                    }
                    if (++n > SPLIT_TRANS_THRESHOLD) {
                        // split trans when folder has large number of children
                        t.commit_();
                        t.end_();
                        t = _tm.begin_();
                        n = 0;
                    }
                }
            }
            finalize_(f.soid, f.historyPath.sid(), t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    private boolean cleanupObject_(OA oa, Path historyPath, Trans t)
            throws SQLException, IOException
    {
        //l.trace("cleanup {}", oa.soid(), historyPath);
        if (oa.fidNoExpulsionCheck() != null && !isStoreStaged_(oa.soid().sidx())) {
            _ds.unsetFID_(oa.soid(), t);
        }
        switch (oa.type()) {
        case FILE:
            cleanupFile_(oa, historyPath, t);
            break;
        case DIR:
            _ps.scrub_(oa.soid(), historyPath, t);

            return _ds.hasChildren_(oa.soid());
        case ANCHOR:
            // anchor should have been scrubbed separately
            // see AdmittedToExpelledAdjuster and StoreDeleter
            break;
        default:
            throw new AssertionError();
        }
        return false;
    }

    /**
     * Cleanup expelled immediately
     */
    private void cleanupFile_(OA oa, Path historyPath, Trans t) throws SQLException, IOException
    {
        SOID soid = oa.soid();
        _ps.scrub_(soid, historyPath, t);

        // if the entire store is staged it's counter-productive to cleanup objects one by one
        if (!isStoreStaged_(soid.sidx())) {
            for (KIndex kidx : oa.casNoExpulsionCheck().keySet()) {
                _ds.deleteCA_(soid, kidx, t);
            }
            _cvc.fileExpelled_(soid, t);
        }

        _pvc.deleteAllPrefixVersions_(soid, t);
    }

    /**
     * If an aliased object is explicitly staged we need to update the staging area db
     * to point to the target object.
     */
    public void objectAliased_(SOID alias, SOID target, Trans t) throws SQLException
    {
        Path historyPath = _sadb.historyPath_(alias);
        if (historyPath != null) {
            _sadb.addEntry_(target, historyPath, t);
            _sadb.removeEntry_(alias, t);
        }
    }
}
