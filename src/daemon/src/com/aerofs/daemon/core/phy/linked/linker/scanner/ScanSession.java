package com.aerofs.daemon.core.phy.linked.linker.scanner;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.first_launch.ScanProgressReporter;
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
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

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
        private final MightCreate _mc;
        private final TimeoutDeletionBuffer _delBuffer;
        private final TransManager _tm;
        private final InjectableFile.Factory _factFile;
        private final ProgressIndicators _pi;
        private final ScanProgressReporter _spr;
        private final ILinkerFilter _filter;

        @Inject
        public Factory(DirectoryService ds,
                MightCreate mc,
                TransManager tm,
                TimeoutDeletionBuffer delBuffer,
                InjectableFile.Factory factFile,
                ScanProgressReporter spr,
                ILinkerFilter filter)
        {
            _ds = ds;
            _mc = mc;
            _tm = tm;
            _spr = spr;
            _pi = ProgressIndicators.get();  // sigh, this should be injected...
            _delBuffer = delBuffer;
            _factFile = factFile;
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

        ExSplitTrans(Trans t, int updates)
        {
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
                String obfuscatedPaths = Joiner.on(", ").join(_sortedPCRoots);
                l.info("[" + obfuscatedPaths + "] " + _recursive);
            }
            _stack = Lists.newLinkedList();
        } else {
            l.info("cont.");
        }

        // If we timed out the previous run or this is our first run
        // make sure remaining paths in sortedPCRoots gets processed.
        if (_stack.isEmpty()) addRootPathComboToStack_();

        // Scan recursively. Stop and request for continuation if needed.
        try {
            int potentialUpdates = 0;
            Trans t = _f._tm.begin_();
            try {
                long start = System.currentTimeMillis();
                while (!_stack.isEmpty()) {
                    try {
                        potentialUpdates += scan_(_stack.pop(), t);
                    } catch (ExSplitTrans e) {
                        potentialUpdates += e._updates;
                        t = e._t;
                    }

                    if (potentialUpdates > CONTINUATION_UPDATES_THRESHOLD) {
                        l.info("exceed updates thres. " + potentialUpdates);
                        break;
                    }
                    if (System.currentTimeMillis() - start > CONTINUATION_DURATION_THRESHOLD) {
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

            // on first launch, report indexing progress
            _f._spr.folderScanned_(potentialUpdates);
        } catch (Exception e) {
            // According to {@link Holder#hold_()}, we have to remove all SOIDs held by us on *any*
            // exception. Note that the objects been removed may include those held in previous
            // continuations of the same session.
            _holder.removeAll_();
            _done = true;
            throw e;
        }


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

    private void addRootPathComboToStack_()
    {
        Iterator<PathCombo> iter = _sortedPCRoots.iterator();
        while (iter.hasNext()) {
            PathCombo pcRoot = iter.next();
            if (isScannableDir(pcRoot._absPath)) {
                // The order of scan is the natural order of the list, as required by the
                // constructor.
                _stack.push(pcRoot);
                iter.remove();
                break;
            } else {
                /* Comment (A), referred to by the constructor, TestScanSession_Misc, MightCreate
                *
                * Skip a root folder if it's missing or no longer a directory. This is to
                * prevent infinite scans on such events. However, we throw and in turn retry
                * from scratch in the event of any error on any non-root objects. Silently
                * ignoring would cause false deletion of objects. For example, if physical
                * folder A is moved to under folder P while A is being scanned, and P has been
                * scanned before, ignoring errors caused by the missing A would lead to
                * deletion of the logical object corresponding to A. This is because the
                * logical object is already in the deletion buffer when A is being scanned.
                */
                l.warn("root " + ObfuscatingFormatters.obfuscatePath(pcRoot._absPath)
                        + " no longer a dir. skip");
                iter.remove();
            }
        }
    }

    /**
     * Scan physical objects under the specified parent folder, stack up child folders if needed.
     * This method must throw on *any* error. See Comment (A) for the rationale.
     *
     * @return maximum number of potential updates generated by this scan.
     */
    private int scan_(PathCombo pcParent, Trans t) throws Exception
    {
        // Locate the logical object corresponding to the physical object. If the logical object is
        // not found, move one folder level up and update pcParent's value and repeat. This is
        // needed because when adding a multi-level folder structure on OSX, the OS may include
        // subfolders before parent folders in a notification batch (see OSXNotifier).
        SOID soidParent;
        while (true) {
            soidParent = _f._ds.resolveNullable_(pcParent._path);
            if (soidParent != null) break;
            // The resolution above must have succeeded if the path is empty.
            assert !pcParent._path.isEmpty() : pcParent._absPath + " " + soidParent;
            pcParent = new PathCombo(_root.absRootAnchor(), pcParent._path.removeLast());
        }

        if (l.isInfoEnabled()) l.info("on " + soidParent + ":" + pcParent);


        OA oaParent = _f._ds.getOA_(soidParent);
        if (_f._filter.shouldIgnoreChilren_(pcParent, oaParent)) return 0;

        addLogicalChildrenToDeletionBuffer_(oaParent);

        // compose the list of physical children
        String[] nameChildren = _f._factFile.create(pcParent._absPath).list();
        if (nameChildren == null) throw new ExNotDir("Not a dir", new File(pcParent._absPath));

        // might create physical children
        int potentialUpdates = 0;
        int n = 0;
        Trans split = null;
        for (String nameChild : nameChildren) {
            PathCombo pcChild = pcParent.append(nameChild);
            MightCreate.Result res = _f._mc.mightCreate_(pcChild, _f._delBuffer,
                    _root.OIDGenerator(), t);

            if ((res == NEW_OR_REPLACED_FOLDER || (_recursive && res == EXISTING_FOLDER))
                && isScannableDir(pcChild._absPath)) {
                // recurse down if it's a newly created folder, or it's an existing folder and the
                // recursive bit is set
                _stack.push(pcChild);

                // remove the child node in the traversal from the list of _sortedPCRoots
                // _sortedPCRoots will eventually contain all the paths that were modified but not
                // touched by the DFS hence we will add those into the stack and continue the scan.
                _sortedPCRoots.remove(pcChild);
            }

            // TODO count only objects that are actually updated.
            if (res != IGNORED) {
                assert res == FILE || res == EXISTING_FOLDER || res == NEW_OR_REPLACED_FOLDER;
                potentialUpdates++;
            }
            _f._pi.incrementMonotonicProgress();

            // commit current trans and start a new one when reaching update threshold
            if (++n == CONTINUATION_UPDATES_THRESHOLD) {
                t.commit_();
                t.end_();
                t = split = _f._tm.begin_();
                n = 0;
            }
        }

        // if a new trans was started we need to inform the caller
        if (split != null) throw new ExSplitTrans(split, potentialUpdates);

        return potentialUpdates;
    }

    private void addLogicalChildrenToDeletionBuffer_(OA oaParent)
            throws SQLException, ExNotDir, ExNotFound
    {
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
        if (soidParent != null) {
            for (OID oid : _f._ds.getChildren_(soidParent)) {
                SOID soid = new SOID(soidParent.sidx(), oid);
                OA oa = _f._ds.getOA_(soid);
                if (!MightDelete.shouldNotDelete(oa)) {
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
