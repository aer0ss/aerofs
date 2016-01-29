package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.FileSystemProber.FileSystemProperty;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.lib.Path;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.C;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A buffer of objects (SOIDs) to be deleted from the system. Objects added to the buffer will be
 * deleted after a timeout elapses. To prevent them from being incorrectly deleted (e.g. during a
 * file move), objects can be removed from the buffer as well.
 */
public class TimeoutDeletionBuffer implements IDeletionBuffer
{
    private static final Logger l = Loggers.getLogger(TimeoutDeletionBuffer.class);

    private final ObjectDeleter _od;
    private final TransManager _tm;
    private final Scheduler _sched;
    private final DirectoryService _ds;
    private final LinkerRootMap _lrm;
    private final InjectableDriver _dr;
    private final IgnoreList _il;
    private final RepresentabilityHelper _rh;
    private final InjectableFile.Factory _factFile;

    private boolean _deletionScheduled = false;

    // Time in milliseconds to delay before deleting an soid
    // (N.B. only package-private for testing purposes)
    static final long TIMEOUT = 6 * C.SEC;

    /**
     * To reduce memory usage in the common case where there is a single Holder,
     * we use a form of dynamic typing for the value of this array. It can be
     * either a Holder object or a Set of such objects. The code ensures that
     * the value will never be an empty set.
     */
    private final Map<SOID, Object> _held = Maps.newHashMap();

    /**
     * Set of objects scheduled for deletion.
     */
    private final Map<SOID, Long> _scheduled = Maps.newHashMap();

    /**
     * There are cases with SOID deletion, where an SOID might be deleted *eventually*, but at
     * present should not be removed from the system. Specifically such cases arise during a scan,
     * where all encountered SOIDs are added to the deletion buffer, then removed from the buffer if
     * a corresponding physical file is found later in the scan. We can not afford to have the
     * deletion timeout run out mid-way during a scan, so for this reason, the scanner uses holders
     * to "hold" an SOID in the buffer preventing it from having a deletion scheduled until the
     * callers "release" the SOID.
     *
     * There may be multiple concurrent scans, and therefore we maintain a set of holders for each
     * SOID. Special care needs to be taken for interaction between these scans. For example, if
     * two scans hold the same SOID, and scan A throws an exception before scan B finishes. The
     * exception causes scan A to invoke removeAll_(), which removes the SOID from the buffer. By
     * the time B finishes and therefore calls releaseAll_(), SOID will not be scheduled for
     * deletion. Because A is responsible to retry and re-delete objects (see comments for hold_()),
     * that SOID will be eventually deleted.
     *
     * Safety Notes:
     * - there are no ordering requirements on add and hold.
     * - remove or removeAll followed by hold will add the SOID back to the buffer.
     *
     * See TestTimeoutDeletionBuffer.java for another summary of assumptions.
     *
     * TODO: for large recursive scans, use multiple Holder if the deletion buffers grows too large
     * This would allow deletions to be acted upon earlier, thereby avoiding crash-loop-inducin OOM,
     * at the expense of a small loss of accuracy where some moves might be interpreted as
     * delete+create sequences.
     */
    public class Holder
    {
        private Holder() {}

        /**
         * Add the SOID to the deletion buffer, but prevent it from being deleted until
         * releaseAll_() is called. Similar to IDeletionBuffer#add_(), removeAll_() must be called
         * on exceptions. The caller is responsible to retry and re-delete the SOIDs after the
         * exception. See add_() for details.
         */
        @SuppressWarnings("unchecked")
        public void hold_(SOID soid)
        {
            checkNotNull(soid);
            Object o = _held.get(soid);
            if (o == null) {
                _held.put(soid, this);
            } else {
                if (o instanceof Holder) {
                    o = Sets.newHashSet((Holder)o);
                    _held.put(soid, o);
                }
                checkState(o instanceof Set);
                checkState(((Set<Holder>)o).add(this), "%s %s", soid, this);
            }
        }

        /**
         * @return SOID released or null if there still exists a hold on that SOID
         */
        @SuppressWarnings("unchecked")
        private @Nullable SOID release_(Iterator<Entry<SOID, Object>> it)
        {
            Entry<SOID, Object> e = it.next();
            if (e.getValue() instanceof Holder) {
                if (e.getValue() != this) return null;
            } else {
                checkState(e.getValue() instanceof Set);
                Set<Holder> s = (Set<Holder>)e.getValue();
                if (!s.remove(this)) return null;
                if (!s.isEmpty()) return null;
            }
            it.remove();
            return e.getKey();
        }

        /**
         * Release all the SOIDs held by the holder. An SOID will be scheduled for deletion when all
         * holders have released it.
         */
        public void releaseAll_()
        {
            Iterator<Entry<SOID, Object>> it = _held.entrySet().iterator();
            while (it.hasNext()) {
                SOID soid = release_(it);
                if (soid != null) scheduleDeletion_(soid);
            }
        }

        /**
         * Remove all the SOIDs held by the holder from the deletion buffer.
         */
        public void removeAll_()
        {
            Iterator<Entry<SOID, Object>> it = _held.entrySet().iterator();
            while (it.hasNext()) {
                SOID soid = release_(it);
                if (soid != null) _scheduled.remove(soid);
            }
        }
    }

    @Inject
    TimeoutDeletionBuffer(ObjectDeleter od, CoreScheduler sched, TransManager tm,
            DirectoryService ds, LinkerRootMap lrm, InjectableDriver dr, IgnoreList il,
            RepresentabilityHelper rh, InjectableFile.Factory factFile)
    {
        _od = od;
        _sched = sched;
        _tm = tm;
        _ds = ds;
        _dr = dr;
        _lrm = lrm;
        _il = il;
        _rh = rh;
        _factFile = factFile;
    }

    @Override
    public void add_(SOID soid)
    {
        checkNotNull(soid);
        scheduleDeletion_(soid);
    }

    @Override
    public void remove_(SOID soid)
    {
        checkNotNull(soid);
        _scheduled.remove(soid);
        _held.remove(soid);
    }

    public Holder newHolder()
    {
        return new Holder();
    }

    private void scheduleDeletion_(SOID soid)
    {
        // If any holders remain on this object, do not bother scheduling a deletion
        if (_held.containsKey(soid)) return;

        // Initialize the deletion time only if it's uninitialized. If we don't do this, in the
        // case of many consecutive scans each of them trying to delete the same object, the object
        // may not get deleted for a long time. As a hypothetical example, a user on OSX (which
        // relies on the scanner) may continuously update a file under a folder where another file
        // is just deleted.
        Long deletionTime = _scheduled.get(soid);
        if (deletionTime == null) {
            // If there are no holders of this SOID, reset its scheduled deletion time,
            // and schedule a deletion event in the future.
            deletionTime = System.currentTimeMillis() + TIMEOUT;
            _scheduled.put(soid, deletionTime);
        }

        l.info("sched delete {} {}", soid, deletionTime);

        if (!_deletionScheduled) {
            _sched.schedule(new AbstractEBSelfHandling() {
                @Override
                public void handle_()
                {
                    _deletionScheduled = false;
                    try {
                        boolean remainingUnheldObjects = executeDeletion_();
                        // only reschedule if there are remaining objects with no holders
                        if (remainingUnheldObjects) {
                            reschedule_();
                        }
                    } catch (Exception e) {
                        l.warn("", e);
                        // Reschedule a later deletion if anything went wrong
                        reschedule_();
                    }
                }

                private void reschedule_()
                {
                    if (!_deletionScheduled) {
                        l.info("Scheduling a consecutive deletion");
                        _sched.schedule(this, TIMEOUT);
                        _deletionScheduled = true;
                    }
                }
            }, TIMEOUT);

            _deletionScheduled = true;
        }
    }

    /**
     * @return whether any objects in the map have zero holders, but were not deleted
     */
    private boolean executeDeletion_() throws Exception
    {
        boolean resched;
        int status;

        do {
            try (Trans t = _tm.begin_()) {
                status = executeDeletion_(System.currentTimeMillis(), t);
                t.commit_();
            }
            resched = (status & RESCHEDULE) != 0;
        } while ((status & CONTINUE) != 0);

        return resched;
    }

    static final int CONTINUE = 1;      // more objects to delete immediately (split trans)
    static final int RESCHEDULE = 2;    // more objects to delete in the future

    private static final int MAX_DELETION_PER_TRANS = 1000;

    /**
     * N.B. this is only package-private so that TestTimeoutDeletionBuffer can access it
     */
    int executeDeletion_(long currentTime, Trans t) throws Exception
    {
        int status = 0;
        final List<SOID> deletedSOIDs = Lists.newLinkedList();

        for (Map.Entry<SOID, Long> entry : _scheduled.entrySet()) {
            SOID soid = entry.getKey();
            Long time = entry.getValue();

            // Delete an object from the system if
            // 1) it has no holders, and
            // 2) its scheduled deletion time has passed
            if (_held.containsKey(soid)) continue;

            // split the transaction
            if (deletedSOIDs.size() > MAX_DELETION_PER_TRANS) {
                status |= CONTINUE;
                break;
            }

            if (time <= currentTime && deleteLogicalObject_(soid, t)) {
                deletedSOIDs.add(soid);
            } else {
                status |= RESCHEDULE;
            }
        }

        // Remove the deleted SOIDs from the buffer *after* all have been successfully deleted
        // i.e. be sure the transaction will complete, before removing the SOIDs
        t.addListener_(new AbstractTransListener() {
            @Override
            public void committed_()
            {
                for (SOID soid : deletedSOIDs) _scheduled.remove(soid);
            }
        });

        return status;
    }

    private boolean deleteLogicalObject_(SOID soid, Trans t) throws Exception
    {
        OA oa = _ds.getOANullable_(soid);
        if (oa == null) {
            // During the aliasing process, we rename the aliased folder,
            // move its contents to the target folder, then delete the aliased folder.
            // When the filesystem watcher observes this last deletion,
            // it will mark a deletion in the TimeoutDeletionBuffer,
            // but the OA for this path will already be null (because by this point,
            // the OA has been merged to the target's already).
            //
            // there is also a potential race condition between observing a deletion
            // in the scanner and the store disappearing altogether (lost ACLs)
            l.info("missing oa {} {}", soid, _ds.hasAliasedOA_(soid));
            return true;
        }
        if (oa.isExpelled()) return true;
        // files with no master branch should not appear in the deletion buffer
        // but we need to be extra defensive here to avoid mistakenly propagating
        // a deletion operation to remote nodes
        if (oa.isFile() && oa.caMasterNullable() == null) return true;
        // likewise, NROs should not appear in the deletion buffer but again we
        // need to be extra defensive about deletions so we double check here
        if (_rh.isNonRepresentable_(oa)) return true;

        if (_scheduled.containsKey(new SOID(soid.sidx(), oa.parent()))) {
            // delay deletion if the parent is also scheduled for deletion
            // when an entire tree is deleted recursively we want to turn this in a single
            // deletion of the top-level folder instead of deletion every object individually
            l.debug("delay deletion {} under {}", soid, oa.parent());
            return false;
        }
        if (physicalObjectDisappeared(oa)) {
            l.info("delete {} {}", soid, oa.name());
            _od.delete_(soid, PhysicalOp.MAP, t);
        }
        return true;
    }

    /**
     * Some users complained about disappearing files and after a refreshing log
     * dive it appeared that one device was deleting logical objects because it
     * thought they had disappeared from the physical filesystem.
     *
     * Although it is quite possible that users are trolling us, it is also
     * possible that a subtle bug in the linker/scanner causes logical objects
     * to remain in the TimeoutDeletionBuffer even though their associated
     * physical objects are still around and unchanged. To get some evidence,
     * one way or another, the following code was added. Before acting on a
     * scheduled deletion we check that the physical object is missing and if
     * something is amiss we send a defect and abort the operation
     */
    private boolean physicalObjectDisappeared(OA oa) throws Exception
    {
        if (_il.isIgnored(oa.name())) return true;

        Path path = _ds.resolve_(oa);
        LinkerRoot lr = _lrm.get_(path.sid());
        if (lr == null) return true;

        String absRoot = lr.absRootAnchor();
        String absPath = path.toAbsoluteString(absRoot);
        InjectableFile f = _factFile.create(absPath);
        if (!existsCaseSensitive(lr, f)) return true;

        FID fid;
        try {
            fid = _dr.getFID(absPath);
            if (fid == null) return true;
        } catch (IOException e) {
            l.warn("failed to get FID, assuming disappeared {} {}", oa.soid(), absPath);
            return true;
        }

        l.warn("here be dragons {} {} {} {}", oa.soid(), oa.fid(), fid, path);
        // something is definitely fishy
        // maybe notifications are slow or downright untrustworthy
        // maybe a large deletion failed halfway through and was rolled back
        // maybe fairies at having some fun at our expense
        //
        // in any case, remove the object from the timeout deletion buffer for now
        // and schedule a scan of the parent folder to bring some measure of harmony
        // back to this wretched world
        lr.scanImmediately_(ImmutableSet.of(f.getParent()), false);
        return false;
    }

    private boolean existsCaseSensitive(LinkerRoot lr, InjectableFile f)
    {
        if (!f.exists()) return false;
        if (!lr.properties().contains(FileSystemProperty.CaseInsensitive)) return true;
        // TODO: add fid->path resolution to Driver to avoid iterating siblings
        String[] siblings = f.getParentFile().list();
        return siblings != null && Arrays.asList(siblings).contains(f.getName());
    }
}
