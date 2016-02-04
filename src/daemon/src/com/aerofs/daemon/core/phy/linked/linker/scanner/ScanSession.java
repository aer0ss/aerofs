package com.aerofs.daemon.core.phy.linked.linker.scanner;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.first_launch.ScanProgressReporter;
import com.aerofs.daemon.core.migration.ImmigrantCreator.ExMigrationDelayed;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper;
import com.aerofs.daemon.core.phy.linked.SharedFolderTagFileAndIcon;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.ILinkerFilter;
import com.aerofs.daemon.core.phy.linked.linker.MightCreate;
import com.aerofs.daemon.core.phy.linked.linker.MightDelete;
import com.aerofs.daemon.core.phy.linked.linker.PathCombo;
import com.aerofs.daemon.core.phy.linked.linker.TimeoutDeletionBuffer;
import com.aerofs.daemon.core.phy.linked.linker.TimeoutDeletionBuffer.Holder;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

import javax.annotation.Nullable;

import static com.aerofs.daemon.core.phy.linked.linker.MightCreate.Result.*;

class ScanSession
{
    private static Logger l = Loggers.getLogger(ScanSession.class);

    // Maximum number of potential updates before requesting for continuation. This threshold is
    // necessary to avoid depleting the heap. (Updates are remembered in memory until the core
    // flushes them periodically. See ComMonitor.)
    private static final int CONTINUATION_UPDATES_THRESHOLD = 1000;

    // Maximum duration in msec before requesting for continuation. This threshold is necessary to
    // avoid the core to become unresponsive due to long scans.
    private static final int CONTINUATION_DURATION_THRESHOLD = 200;

    static class Factory
    {
        private final DirectoryService _ds;
        private final RepresentabilityHelper _rh;
        private final MightCreate _mc;
        private final TimeoutDeletionBuffer _delBuffer;
        private final TransManager _tm;
        private final InjectableFile.Factory _factFile;
        private final ProgressIndicators _pi;
        private final ScanProgressReporter _spr;
        private final ILinkerFilter _filter;
        private final SharedFolderTagFileAndIcon _sfti;

        @Inject
        public Factory(DirectoryService ds,
                RepresentabilityHelper rh,
                MightCreate mc,
                TransManager tm,
                TimeoutDeletionBuffer delBuffer,
                InjectableFile.Factory factFile,
                ScanProgressReporter spr,
                SharedFolderTagFileAndIcon sfti,
                ILinkerFilter filter)
        {
            _ds = ds;
            _mc = mc;
            _tm = tm;
            _rh = rh;
            _spr = spr;
            _pi = ProgressIndicators.get();  // sigh, this should be injected...
            _delBuffer = delBuffer;
            _factFile = factFile;
            _sfti = sfti;
            _filter = filter;
        }

        public ScanSession create_(LinkerRoot root, Set<String> absPaths, boolean recursive)
        {
            return new ScanSession(this, root, absPaths, recursive);
        }
    }

    private final Factory _f;

    private final LinkerRoot _root;

    // set of path combos that represent all "root" folders of the scan session ({@code absPaths})
    private LinkedHashSet<PathCombo> _sortedPCRoots;

    // true iff subfolders should be scanned recursively
    private final boolean _recursive;

    // Encountered SOIDs in a scan session will be added to the deletion
    // buffer, then removed if a file is physically present which represents them. Instead of
    // simply adding SOIDs to the buffer, we "hold" them, so that their deletion cannot be
    // scheduled until after the SOIDs have been "released." The following set tracks which
    // SOIDs have been held by this particular (local) scan. At the end of the scan,
    // we iterate over the set and release all the locally-held SOIDs.
    private final Holder _holder;

    // Don't use java.util.Stack as it's backed by Vector which is synchronized. We don't need
    // synchronization here. A null stack indicates that scan_() has never been called.
    private @Nullable Deque<PathCombo> _stack;

    // For debugging only
    private boolean _done;

    /**
     * @param absPaths the set of absolute paths to be scanned. The natural order of the set
     * specifies the scan order. Each path should refer to an existing folder. Non-folder or
     * non-existent paths are ignored. See Comment (A) for rationale.
     * @param recursive whether to recurse down to subfolders for each specified pat
     */
    private ScanSession(Factory f, LinkerRoot root, Set<String> absPaths, boolean recursive)
    {
        _f = f;
        _root = root;

        List<PathCombo> sortedAbsPaths = Lists.newArrayListWithCapacity(absPaths.size());
        for (String absPath : absPaths) {
            PathCombo pc = new PathCombo(_root.sid(), _root.absRootAnchor(), absPath);
            sortedAbsPaths.add(pc);
        }

        // Sort the paths such that parent paths will appear before the child paths
        // helping us to not traverse the same path multiple times.
        Collections.sort(sortedAbsPaths);
        _sortedPCRoots = Sets.newLinkedHashSet(sortedAbsPaths);

        _recursive = recursive;
        _holder = f._delBuffer.newHolder();
    }

    /**
     * If a single folder has a large amount of children it is necessary to split the scan in
     * multiple transactions to prevent OOMs arising from explosive growth of TransLocal data and
     * significant slowdown caused by uncontrolled growth of the WAL file
     */
    private class ExSplitTrans extends Exception
    {
        private static final long serialVersionUID = 0L;

        final Trans _t;
        final int _updates;

        ExSplitTrans(Trans t, int updates, Throwable cause)
        {
            super(cause);
            _t = t;
            _updates = updates;
        }
    }

    /**
     * Start the scan, or continue from the previous state if continuation was requested during the
     * last call of this method.
     *
     * @return true if the scan is complete or false if continuation is requested, The caller should
     * call this method repeatedly until it returns true. The caller may release the core lock
     * between calls to avoid the core from being unresponsive.
     */
    boolean scan_() throws Exception
    {
        // can't be called if this scan session is done.
        assert !_done;

        // Initialization
        if (_stack == null) {
            if (l.isInfoEnabled()) {
                l.info("[{}] {}", Joiner.on(", ").join(_sortedPCRoots), _recursive);
            }
            _stack = Lists.newLinkedList();
        } else {
            l.info("cont.");
        }

        Set<PathCombo> delayed = new HashSet<>();
        Deque<PathCombo> backupStack = new LinkedList<>(_stack);
        Set<PathCombo> backupScanned = new HashSet<>(_scanned);
        LinkedHashSet<PathCombo> backupRoots = new LinkedHashSet<>(_sortedPCRoots);
        do {
            // If we timed out the previous run or this is our first run
            // make sure remaining paths in sortedPCRoots gets processed.
            if (_stack.isEmpty()) addRootPathComboToStack_();

            // Scan recursively. Stop and request for continuation if needed.

            try {
                scanLoop_(delayed);

                // if we had to skip over any item, the scan session should be considered a failure,
                // even if some progress was made
                if (!delayed.isEmpty()) {
                    throw new Exception("delayed: " + delayed.size());
                }
            } catch (Exception e) {
                // According to {@link Holder#hold_()}, we have to remove all SOIDs held by us on *any*
                // exception. Note that the objects been removed may include those held in previous
                // continuations of the same session.
                _holder.removeAll_();
                if (e instanceof ExMigrationDelayed) {
                    l.warn("delayed migration: {}", e.getMessage());
                    _sortedPCRoots = backupRoots;
                    _scanned = backupScanned;
                    _stack = backupStack;
                    // retry scanning, this time skipping over the object whose migration failed
                    continue;
                }
                _done = true;
                throw e;
            }
        } while (!delayed.isEmpty());

        ////////
        // Finalization. No actual file operation is allowed beyond this point. All operations
        // should have been done in the above transaction.

        if (!_stack.isEmpty() || !_sortedPCRoots.isEmpty()) {
            l.info("cont. pending");
            return false;
        } else {
            // Release all SOIDs which were held during the scan (permitting their deletion to
            // be scheduled). If an SOID was removed from the deletion buffer during the scan,
            // the release is a no-op.
            // N.B. No code after this line should throw.
            _holder.releaseAll_();
            l.info("end");
            _done = true;
            return true;
        }
    }

    private void scanLoop_(Set<PathCombo> delayed) throws Exception {
        int potentialUpdates = 0;
        Trans t = _f._tm.begin_();
        try {
            ElapsedTimer timer = new ElapsedTimer();
            while (!_stack.isEmpty()) {
                PathCombo pc = _stack.pop();
                try {
                    potentialUpdates += scanFolder_(pc, delayed, t);
                } catch (ExSplitTrans e) {
                    potentialUpdates += e._updates;
                    t = e._t;
                    Throwable cause = e.getCause();
                    if (cause != null) {
                        Throwables.propagateIfPossible(cause, Exception.class);
                        throw new RuntimeException(cause);
                    }
                }

                if (potentialUpdates > CONTINUATION_UPDATES_THRESHOLD) {
                    l.info("exceed updates thres. " + potentialUpdates);
                    break;
                }
                if (timer.elapsed() > CONTINUATION_DURATION_THRESHOLD) {
                    l.info("exceed duration thres. " + potentialUpdates);
                    break;
                }

                // If we have any remaining elements in sortedPCRoots that weren't touched
                // by the DFS add them into the stack once we are done scanning one subtree.
                if (_stack.isEmpty()) addRootPathComboToStack_();
            }
            t.commit_();
        } finally {
            t.end_();
        }
    }

    private void addRootPathComboToStack_()
    {
        Iterator<PathCombo> iter = _sortedPCRoots.iterator();
        if (!iter.hasNext()) return;
        PathCombo pcRoot = iter.next();
        while (!(pcRoot._path.isEmpty() || isScannableDir(pcRoot._absPath))) {
            /*
             * In a perfect world this race condition could be safely ignored, however
             * nothing is ever quite that simple in this wretched world. One would think
             * that if a folder disappears we would be notified about it. To be fair, we
             * sort of are, just not necessarily the way the docs say it should happen...
             *
             * On OSX deletion of folders should be reported as changes to be scanned in
             * the parent folders, which is mostly what happens, except sometimes we
             * instead get notifications of changes in the deleted folders and none for
             * the parent folders, in which case safety can only be achieved by forcing
             * a re-scan of the parent.
             */
            l.warn("{} no longer a dir. go up", pcRoot._absPath);
            pcRoot = pcRoot.parent();
        }
        iter.remove();
        _stack.push(pcRoot);
    }

    private Set<PathCombo> _scanned = new HashSet<>();

    /**
     * Scan physical objects under the specified parent folder, stack up child folders if needed.
     * This method must throw on *any* error. See Comment (A) for the rationale.
     *
     * @return maximum number of potential updates generated by this scan.
     */
    private int scanFolder_(PathCombo pcParent, Set<PathCombo> delayed, Trans t) throws Exception
    {
        // a given path may not be scanned twice in the same ScanSession
        // otherwise children would be held twice in TimeoutDeletionBuffer, triggering an AE
        if (!_scanned.add(pcParent)) return 0;

        // empty path <=> physical root
        // make sure the tag file is kept correct for all physical roots
        if (pcParent._path.isEmpty()) {
            _f._sfti.fixTagFileIfNeeded_(pcParent._path.sid(), pcParent._absPath);
        }
        if (!addLogicalChildrenToDeletionBufferIfScanNeeded_(pcParent)) return 0;

        // compose the list of physical children
        String[] nameChildren = _f._factFile.create(pcParent._absPath).list();
        if (nameChildren == null) throw new ExNotDir("Not a dir", new File(pcParent._absPath));

        // might create physical children
        int potentialUpdates = 0;
        int scannedChildren = 0;
        int lastNotification = 0;
        Trans split = null;
        try {
            for (String nameChild : nameChildren) {
                PathCombo pc = pcParent.append(nameChild);
                if (delayed.contains(pc)) {
                    l.debug("skip delayed: {}", pc);
                    continue;
                }
                try {
                    if (mightCreate_(pc, t)) ++potentialUpdates;
                } catch (ExMigrationDelayed e) {
                    delayed.add(pc);
                    throw e;
                }

                // commit current trans and start a new one when reaching update threshold
                if (++scannedChildren == CONTINUATION_UPDATES_THRESHOLD) {
                    t.commit_();
                    t.end_();
                    t = split = _f._tm.begin_();
                    // on first launch, report indexing progress
                    _f._spr.filesScanned_(potentialUpdates - lastNotification);
                    lastNotification = potentialUpdates;
                    scannedChildren = 0;
                }
            }

            // on first launch, report indexing progress
            _f._spr.folderScanned_(potentialUpdates - lastNotification);

        } catch (Exception|Error e) {
            if (split != null) {
                // if an exception is thrown after a new trans was started we need to make
                // sure the caller is aware of it otherwise it will try to rollback an already
                // committed transaction and will trigger an assertion error.
                throw new ExSplitTrans(split, potentialUpdates, e);
            } else {
                throw e;
            }
        }
        // if a new trans was started we need to inform the caller
        if (split != null) throw new ExSplitTrans(split, potentialUpdates, null);
        return potentialUpdates;
    }

    /**
     * @return true if the given path was "maybe updated"
     */
    private boolean mightCreate_(PathCombo pc, Trans t) throws Exception
    {
        MightCreate.Result res = _f._mc.mightCreate_(pc, _f._delBuffer, _root.OIDGenerator(), t);

        if ((res == NEW_OR_REPLACED_FOLDER || (_recursive && res == EXISTING_FOLDER))
                && isScannableDir(pc._absPath)) {
            // recurse down if it's a newly created folder, or it's an existing folder and the
            // recursive bit is set
            _stack.push(pc);

            // remove the child node in the traversal from the list of _sortedPCRoots
            // _sortedPCRoots will eventually contain all the paths that were modified but not
            // touched by the DFS hence we will add those into the stack and continue the scan.
            _sortedPCRoots.remove(pc);
        }
        _f._pi.incrementMonotonicProgress();
        return res != IGNORED;
    }

    /**
     * @return whether the given path needs to be scanned
     */
    private boolean addLogicalChildrenToDeletionBufferIfScanNeeded_(PathCombo pcParent) throws Exception
    {
        SOID soidParent = _f._ds.resolveNullable_(pcParent._path);
        if (soidParent == null) {
            // mkay, so we're supposed to scan a physical folder, except we have no corresponding
            // logical object at that path, which the scan flow should ensure and is required to,
            // you know, add children...
            //
            // Possible scenarios:
            //      * scan interrupted and folder deleted before continuations
            //      * scan triggered by notifications about deleted object(s)
            //      * scan triggered by notification about children of ignored folder(s)
            //      * something else?
            //
            // In any case the only safe thing to do is to ignore that path for now.
            //
            // TODO: schedule a scan of the parent to make sure we don't somehow lose updates?
            // Theoretically this should be unnecessary but the thing about theory is that it
            // rarely ever holds in practice, especially so when dealing with filesystems...
            l.warn("no parent, no scan {}", pcParent._path);
            return false;
        }

        l.info("on {}:{}", soidParent, pcParent);

        OA oaParent = _f._ds.getOA_(soidParent);
        if (_f._filter.shouldIgnoreChilren_(pcParent, oaParent)) {
            l.warn("ignore children under {} {}", soidParent, pcParent);
            return false;
        }

        // NB: it is VERY IMPORTANT to call that AT MOST ONCE per SOID per scan
        // every time you violate that contract TimeoutDeletionBuffer devours the soul
        // of an innocent kitten
        addLogicalChildrenToDeletionBuffer_(oaParent);
        return true;
    }

    private void addLogicalChildrenToDeletionBuffer_(OA oaParent)
            throws SQLException, ExNotDir, ExNotFound
    {
        // It is possible to run into a non-representable object during a scan, e.g. :
        //
        // virtual:
        //      foo/
        //          bar
        //          BAR
        //
        // physical:
        //      foo/
        //          bar
        //      .aerofs.aux.<SID>/
        //          <soid:BAR>
        //
        // user deletes "bar" and create new "BAR" before AeroFS picks up deletion (either because
        // AeroFS is not running or because the creation happens before the "bar" leaves the
        // TimeoutDeletionBuffer).
        //
        // In such cases, we MUST NOT add the children of BAR to the deletion buffer. MightCreate
        // will then take care of renaming the old "BAR" to avoid a conflict.
        if (!oaParent.soid().oid().isRoot() && _f._rh.isNonRepresentable_(oaParent)) return;

        // the caller guarantees that the OA is not null
        SOID soidParent = oaParent.soid();
        if (oaParent.isAnchor()) {
            // assign the anchored store's root folder as the parent. Note that soidParent may be
            // null if the anchor is expelled.
            soidParent = _f._ds.followAnchorNullable_(oaParent);
        } else if (oaParent.isFile()) {
            // do nothing but rely on the getChildren() below to throw ExNotDir
        }

        // add logical children to the deletion buffer
        if (soidParent == null) return;
        try (IDBIterator<OID> it = _f._ds.listChildren_(soidParent)) {
            while (it.next_()) {
                SOID soid = new SOID(soidParent.sidx(), it.get_());
                OA oa = _f._ds.getOA_(soid);
                // avoid placing objects in deletion buffer if we know they won't appear in a scan:
                // 1. expelled objects
                // 2. files whose master branch was not successfully downloaded yet
                // 3. non-representable objects
                if (!(MightDelete.shouldNotDelete(oa) || _f._rh.isNonRepresentable_(oa))) {
                    l.debug("hold_ on {}", soid);
                    _holder.hold_(soid);
                }
                _f._pi.incrementMonotonicProgress();
            }
        }
    }

    /**
     * Return true if the path describes a directory that we can read,
     * and have a chance at actually syncing.
     */
    private boolean isScannableDir(String absPath)
    {
        InjectableFile ifile = _f._factFile.create(absPath);
        return ifile.isDirectory() && ifile.canRead();
    }
}
