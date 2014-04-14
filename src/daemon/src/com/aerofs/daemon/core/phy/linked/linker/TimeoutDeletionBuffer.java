package com.aerofs.daemon.core.phy.linked.linker;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper;
import com.aerofs.lib.Path;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A buffer of objects (SOIDs) to be deleted from the system. Objects added to the buffer will be
 * deleted after a timeout elapses. To prevent them from being incorrectly deleted (e.g. during a
 * file move), objects can be removed from the buffer as well.
 */
public class TimeoutDeletionBuffer implements IDeletionBuffer
{
    private static final Logger l = Loggers.getLogger(TimeoutDeletionBuffer.class);

    // use linked hash map rather than hash map to speed up key iteration.
    private final Map<SOID, TimeAndHolders> _soid2th = Maps.newLinkedHashMap();
    private final ObjectDeleter _od;
    private final TransManager _tm;
    private final Scheduler _sched;
    private final DirectoryService _ds;
    private final LinkerRootMap _lrm;
    private final InjectableDriver _dr;
    private final IgnoreList _il;
    private final RepresentabilityHelper _rh;

    private boolean _deletionScheduled = false;

    // Time in milliseconds to delay before deleting an soid
    // (N.B. only package-private for testing purposes)
    static final long TIMEOUT = 6 * C.SEC;

    private static class TimeAndHolders
    {
        // Time in milliseconds since 1970. 0 if uninitialized.
        long _time;
        // Allow nullable to avoid creating new objects when _holders are not referred to.
        private @Nullable Set<Holder> _holders;

        boolean hasHolders_()
        {
            return _holders != null && !_holders.isEmpty();
        }

        boolean addHolder_(Holder h)
        {
            if (_holders == null) _holders = Sets.newHashSet();
            return _holders.add(h);
        }

        /**
         * @return whether the object contains the specified holder
         * @pre h must have been added before
         */
        boolean removeHolder_(Holder h)
        {
            return _holders != null && _holders.remove(h);
        }

        boolean hasHolder_(Holder h)
        {
            return _holders != null && _holders.contains(h);
        }
    }

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
        public void hold_(SOID soid)
        {
            assert soid != null;
            TimeAndHolders th = _soid2th.get(soid);
            if (th == null) {
                th = new TimeAndHolders();
                _soid2th.put(soid, th);
            }
            boolean added = th.addHolder_(this);
            assert added : soid + " " + this;
        }

        /**
         * Release all the SOIDs held by the holder. An SOID will be scheduled for deletion when all
         * holders have released it.
         */
        public void releaseAll_()
        {
            for (Entry<SOID, TimeAndHolders> e : _soid2th.entrySet()) {
                if (e.getValue().removeHolder_(this)) scheduleDeletion_(e.getKey(), e.getValue());
            }
        }

        /**
         * Remove all the SOIDs held by the holder from the deletion buffer.
         */
        public void removeAll_()
        {
            // use a separate set to avoid concurrent modifications. alternatively, we can use
            // iterator.remove().
            List<SOID> soids = Lists.newArrayListWithExpectedSize(_soid2th.size());
            for (Entry<SOID, TimeAndHolders> en : _soid2th.entrySet()) {
                if (en.getValue().hasHolder_(this)) soids.add(en.getKey());
            }
            for (SOID soid : soids) remove_(soid);
        }
    }

    @Inject
    TimeoutDeletionBuffer(ObjectDeleter od, CoreScheduler sched, TransManager tm,
            DirectoryService ds, LinkerRootMap lrm, InjectableDriver dr, IgnoreList il,
            RepresentabilityHelper rh)
    {
        _od = od;
        _sched = sched;
        _tm = tm;
        _ds = ds;
        _dr = dr;
        _lrm = lrm;
        _il = il;
        _rh = rh;
    }

    @Override
    public void add_(SOID soid)
    {
        assert soid != null;
        TimeAndHolders th = _soid2th.get(soid);
        if (th == null) {
            th = new TimeAndHolders();
            _soid2th.put(soid, th);
        }
        scheduleDeletion_(soid, th);
    }

    @Override
    public void remove_(SOID soid)
    {
        assert soid != null;
        _soid2th.remove(soid);
    }

    public Holder newHolder()
    {
        return new Holder();
    }

    private void scheduleDeletion_(SOID soid, TimeAndHolders th)
    {
        // If any holders remain on this object, do not bother scheduling a deletion
        if (th.hasHolders_()) return;

        // Initialize the deletion time only if it's uninitialized. If we don't do this, in the
        // case of many consecutive scans each of them trying to delete the same object, the object
        // may not get deleted for a long time. As a hypothetical example, a user on OSX (which
        // relies on the scanner) may continuously update a file under a folder where another file
        // is just deleted.
        if (th._time == 0) {
            // If there are no holders of this SOID, reset its scheduled deletion time,
            // and schedule a deletion event in the future.
            long deletionTime = System.currentTimeMillis() + TIMEOUT;
            // A previously scheduled deletion should never be later than a newly scheduled time
            assert th._time <= deletionTime;
            th._time = deletionTime;
        }

        l.info("sched delete {} {}", soid, th._time);

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
                        l.warn(Util.e(e));
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
        boolean retval;
        Trans t = _tm.begin_();
        try {
            retval = executeDeletion_(System.currentTimeMillis(), t);
            t.commit_();
        } finally {
            t.end_();
        }
        return retval;
    }

    /**
     * N.B. this is only package-private so that TestTimeoutDeletionBuffer can access it
     * @return whether any objects in the map have zero holders, but were not deleted
     */
    boolean executeDeletion_(long currentTime, Trans t) throws Exception
    {
        boolean remainingUnheldObjects = false;
        List<SOID> deletedSOIDs = Lists.newLinkedList();

        // NB: if this loop ever proves to be a performance issue, use a priority queue that orders
        // elements by their scheduled deletion time
        for (Map.Entry<SOID, TimeAndHolders> entry : _soid2th.entrySet()) {
            TimeAndHolders th = entry.getValue();

            // Delete an object from the system if
            // 1) it has no holders, and
            // 2) its scheduled deletion time has passed
            if (!th.hasHolders_()) {
                if (th._time <= currentTime) {
                    SOID soid = entry.getKey();
                    if (deleteLogicalObject_(soid, t)) {
                        deletedSOIDs.add(soid);
                    }
                } else {
                    // This object has no holders, but its scheduled deletion time has not passed,
                    // so there are remaining unheld objects.
                    remainingUnheldObjects = true;
                }
            }
        }

        // Remove the deleted SOIDSs from the buffer *after* all have been successfully deleted
        // i.e. be sure the transaction will complete, before removing the SOIDs
        for (SOID soid : deletedSOIDs) remove_(soid);

        return remainingUnheldObjects;
    }

    private boolean deleteLogicalObject_(SOID soid, Trans t) throws Exception
    {
        if (!_ds.hasOA_(soid)) {
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
            return false;
        } else {
            OA oa =  _ds.getOA_(soid);

            if (physicalObjectDisappeared(oa)) {
                l.info("delete {} {}", soid,
                        ObfuscatingFormatters.obfuscatePath(oa.name()));
                _od.delete_(soid, PhysicalOp.MAP, t);
            }
            return true;
        }
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
        // files with no master branch should not appear in the deletion buffer
        // but we need to be extra defensive here to avoid mistakenly propagating
        // a deletion operation to remote nodes
        if (oa.isFile() && oa.caMasterNullable() == null) return false;
        // likewise, NROs should not appear in the deletion buffer but again we
        // need to be extra defensive about deletions so we double check here
        if (_rh.isNonRepresentable(oa)) return false;

        Path path = _ds.resolve_(oa);
        String absRoot = _lrm.absRootAnchor_(path.sid());
        if (absRoot != null) {
            String absPath = path.toAbsoluteString(absRoot);
            File f = new File(absPath);
            if (f.exists() && !_il.isIgnored(f.getName())) {
                // ok, so the file we're supposed to be deleting exists, but wait, is it actually
                // the same file? Path are pretty useless on case-insensitive filesystems so we
                // check type and FID instead.
                FID fid;
                try {
                    fid = _dr.getFID(absPath);
                    if (fid == null) return true;
                } catch (IOException e) {
                    l.warn("failed to get FID, assuming disappeared {} {}", oa.soid(), absPath);
                    return true;
                }

                l.warn("here be dragons {} {}", oa.soid(), oa.fid(), fid,
                        ObfuscatingFormatters.obfuscatePath(path));
                // if the physical object has different type or FID, proceed with the deletion
                if (f.isFile() == oa.isFile() && fid.equals(oa.fid())) {
                    // physical object with same FID and same type at the same path:
                    // 1. submit a defect
                    // 2. prevent deletion of logical object
                    // 3. allow removal of object from deletion buffer
                    //
                    // NB: this can happen if a remote-initiated deletion or a locally initiated
                    // expulsion affects a large tree and fails. In that case the MCN may take too
                    // long to reach the core so the TDB is full of OIDs not actually deleted.
                    //
                    // TODO: schedule full scan?
                    /**
                     *  The following is commented out because the defects are overloading Rocklog.
                     *  While this doesn't affect many users, each affected user tends to flood
                     *  Rocklog with many defect reports thus overloading Rocklog.
                     *
                     *  TODO: investigate the issue and come up with a resolution
                    _rocklog.newDefect("daemon.linker.tdb")
                            .setMessage(ObfuscatingFormatters.obfuscatePath(path) + " " + oa)
                            .send();
                     */
                    return false;
                }
            }
        }
        return true;
    }
}
