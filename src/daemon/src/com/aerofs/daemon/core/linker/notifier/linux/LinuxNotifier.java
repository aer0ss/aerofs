package com.aerofs.daemon.core.linker.notifier.linux;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.aerofs.swig.driver.Driver;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import com.aerofs.daemon.core.linker.Linker;
import com.aerofs.daemon.core.linker.event.EIMightCreateNotification;
import com.aerofs.daemon.core.linker.event.EIMightDeleteNotification;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.linker.notifier.INotifier;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.injectable.InjectableJNotify;

import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.linux.INotifyListener;

import javax.annotation.Nullable;

import static net.contentobjects.jnotify.linux.JNotify_linux.*;

public class LinuxNotifier implements INotifier, INotifyListener
{
    private final static Logger l = Loggers.getLogger(LinuxNotifier.class);
    final CfgAbsRootAnchor _cfgAbsRootAnchor;
    final CoreQueue _cq;
    final InjectableJNotify _jn;
    // The list of possible event types.  We should update on file creation, deletion,
    // modification, rename/move, and metadata change (timestamps, permissions, etc).
    final static int MASK = IN_CREATE | IN_DELETE | IN_MODIFY |
            IN_MOVED_FROM | IN_MOVED_TO | IN_ATTRIB ;

    // All of the remaining instance variables below should be protected by synchronized(this)
    // everywhere they are accessed, since JNotify spawns a thread to watch for inotify events.  A
    // SynchronizedMap is unsatisfactory; it won't actually prevent concurrent access,
    // just throw an exception when it happens.
    private final Map<Integer, LinuxINotifyWatch> _watches = Maps.newHashMap();

    // A set of watches for which we desire to ignore incoming events,
    // but for which the kernel has not yet acknowledged that request.
    private final Set<Integer> _deletedAckPending = Sets.newHashSet();

    // These are for dealing with connecting IN_MOVED_FROM with IN_MOVED_TO events, so we don't
    // have to unregister and reregister all of the related watches.  We make an assumption that
    // MOVED_FROM and MOVED_TO events will come as a pair, which, while not strictly true, improves
    // performance drastically in the case that they do, which is the usual one, and does not lose
    // correctness even in the case that they don't.
    private int _prevActionId = -1;
    private int _prevActionMask = 0;
    private int _prevCookie = 0;
    private String _prevName;

    final static int WATCH_ID_ROOT = 0; // Linux never gives 0 as a watch ID, so we use it as the
                                        // "parent" of the watch on the root anchor.

    public LinuxNotifier(CoreQueue cq, InjectableJNotify jn, CfgAbsRootAnchor cfgAbsRootAnchor)
    {
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
        _cq = cq;
        _jn = jn;
    }

    @Override
    public void start_() throws JNotifyException
    {
       // Watch the root anchor, and (recursively) all directories within
        _jn.linux_setNotifyListener(this);
        File anchor = new File(_cfgAbsRootAnchor.get());
        addWatchRecursively(anchor.getAbsolutePath(), WATCH_ID_ROOT, null);
    }

    /**
     * Returns the filesystem path believed to be associated with the given watch ID.
     * @param watch_id the watch id for which to retrieve a path
     * @return the absolute abstract path that LinuxNotifier believes is associated with watch_id
     */
    private synchronized File getWatchPath(int watch_id)
    {
        assert(watch_id > 0);
        // Walk up the parent tree to the root node, storing watches along the way
        List<Integer> parentTrace = new ArrayList<Integer>();
        int next_id = watch_id;
        while(next_id != WATCH_ID_ROOT) {
            parentTrace.add(0, next_id);
            next_id = _watches.get(next_id)._parentWatchId;
        }
        File f = new File(_watches.get(parentTrace.get(0))._name);
        for(int i = 1 ; i < parentTrace.size() ; i++) {
            f = new File(f, _watches.get(parentTrace.get(i))._name);
        }
        return f;
    }

    /**
     * Add a watch on the path "name" underneath the folder associated with a parent watch id.
     * If the parent watch id is WATCH_ID_ROOT, it adds a watch on the root anchor.
     * @param name the name of the folder to add a watch on (this should have no path separators)
     * @param parent the watch id of the watch on the directory under which this watch is
     *               placed, or WATCH_ID_ROOT if this is the root anchor
     * @param pathList a list of path for which watch were added (output arg, if not null)
     * @throws JNotifyException
     */
    private void addWatchRecursively(String name, int parent, @Nullable List<String> pathList)
            throws JNotifyException
    {
        File dir;
        if (parent == WATCH_ID_ROOT) {
            dir = new File(name);
        } else {
            dir = new File(getWatchPath(parent), name);
        }
        // inotify never gives 0 as a watch number.  -1 is used for errors.
        int watch_id = -1;
        synchronized (this) {
            try {
                l.debug("addWatchRecursively(" + dir + ")");
                watch_id = _jn.linux_addWatch(dir.getAbsolutePath(), MASK);
                assert(watch_id != WATCH_ID_ROOT);
                if (l.isDebugEnabled()) {
                    l.debug("watch id " + watch_id + ": " + dir.getAbsolutePath());
                }
                if (pathList != null) pathList.add(dir.getAbsolutePath());
                LinuxINotifyWatch newWatch = new LinuxINotifyWatch(watch_id, name, parent);
                // We may already have a watch set up on this folder.  If the path of the
                // existing watch and the path of the new watch are identical,
                // that's fine - the kernel gives out the same watch id if you request
                // multiple watches on the same inode. If the paths differ,
                // that's still possible in normal operation (see below),
                // but is unusual enough to merit its own log line.
                // We make the first case here a logging no-op.
                if (_watches.containsKey(watch_id)) {
                    if (l.isDebugEnabled()) {
                        l.debug("duplicate watch id " + watch_id + " for " +
                                dir.getAbsolutePath() + " and " +
                                getWatchPath(watch_id).getAbsolutePath());
                    }
                    // We cannot safely make the following assertion due to the following flow:
                    // 1) + folder
                    // 2) + folder/inner
                    // 3)   folder -> folder2
                    // 4) + folder
                    // 5)   folder2/inner -> folder/inner
                    // If event 5 happens on the filesystem before we process event 4, then when
                    // we go to add a recursive watch on folder, we'll see inner,
                    // try to add a watch, and get back a duplicate watch id.
                    // Personal opinion: filesystem watching is hard. :/
                    //assert(getWatchPath(watch_id).equals(dir));
                } else {
                    // We obtained a new watch.  We should save it in our map.
                    _watches.put(watch_id, newWatch);
                    if(parent != WATCH_ID_ROOT) {
                        _watches.get(parent)._children.add(newWatch);
                    }
                }
            } catch (JNotifyException e) {
                // There's no great way to deal with running out of inotify watches,
                // but we can at least put something uniquely identifying in the log and sync what
                // we can watch, rather than aborting entirely.
                if (e.getErrorCode() == JNotifyException.ERROR_WATCH_LIMIT_REACHED) {
                    l.error("inotify watches have been exhauseted. AeroFS needs more than the " +
                            "maximum number of inotify watches allowed for this user to sync all " +
                            "of your folders.  See" +
                            "http://support.aerofs.com/knowledgebase/articles/47290-some-of-my-files-did-not-sync- " +
                            "for more information.");
                    return;
                }
                // It's possible that the folder will disappear while we're trying to add a watch.
                // This is okay, but we should no longer look at child folders.  Other
                // failures should be passed up.
                if (e.getErrorCode() != JNotifyException.ERROR_NO_SUCH_FILE_OR_DIRECTORY) {
                    throw e;
                }
                return;
            }
        }
        assert(watch_id != -1);
        String[] childrennames = dir.list();
        if (childrennames == null) return; // if the directory was removed, children will be null.
        for (String childname : childrennames) {
            File child = new File(dir, childname);
            // We should only add watches on children that are actually directories.
            // Unfortunately, File.isDirectory() does not distinguish between folders and symlinks
            // to folders, so we call into Driver (which distinguishes between the two).
            // N.B. Driver.getFid requires null for the first parameter (swig oddity), and accepts a
            // byte buffer as the third argument (in which it places the FID).  Since we don't
            // actually care about the FID, we just pass null (which is correctly handled by Driver)
            if (Driver.getFid(null, child.getPath(), null) == Driver.GETFID_DIR) {
                try {
                    addWatchRecursively(childname, watch_id, pathList);
                } catch (JNotifyException e) {
                    // See above.
                    if (e.getErrorCode() != JNotifyException.ERROR_NO_SUCH_FILE_OR_DIRECTORY) {
                        throw e;
                    }
                }
            }

        }
    }

    /**
     * Cancels a watch, and all of its children, recursively, and marks their watch IDs as
     * ignored, should we still receive events associated with them
     * @param watch the LinuxINotifyWatch to cancel
     * @throws JNotifyException
     */
    private synchronized void removeWatchRecursively(LinuxINotifyWatch watch)
            throws JNotifyException
    {
        if (l.isDebugEnabled()) {
            l.debug("removeWatchRecursively( id=" + watch._watchId + ", path=" +
                    getWatchPath(watch._watchId).getAbsolutePath() + ")" );
        }
        for(LinuxINotifyWatch child : watch._children) {
            removeWatchRecursively(child);
        }
        watch._children.clear();
        try {
            _jn.linux_removeWatch(watch._watchId);
        } catch (JNotifyException e) {
            // It's possible (particularly with moves and deletions) that a directory on which we
            // would cancel a watch already had the watch removed by the kernel, and we're just
            // slow at processing its removal.  We should handle this case gracefully too.
            //
            // However, JNotify does not properly propagate errno up when produced by
            // remove_watch(), so we work around this here.  TODO: patch this upstream
            l.warn("removeWatch threw: " + e.getErrorCode() + e.getMessage());
            if (e.getErrorCode() != JNotifyException.ERROR_UNSPECIFIED) {
                throw e;
            }
        }
        if (!_deletedAckPending.contains(watch._watchId)) {
            _deletedAckPending.add(watch._watchId);
        }
        if (l.isDebugEnabled()) {
            l.debug("Removed watch " + watch._watchId + " on path "
                   + getWatchPath(watch._watchId).getAbsolutePath());
        }
    }

    /**
     * The callback function that JNotify will call.  The parameters match those noted in
     * the inotify(2) manpage.
     * @param name The filename or foldername under the watched folder to which this event pertains
     * @param id The watch id for which this event was generated
     * @param mask A bitmask of event attributes that describe this event
     * @param cookie A unique number associating paired IN_MOVED_FROM and IN_MOVED_TO events
     */
    @Override
    public void notify(String name, int id, int mask, int cookie)
    {
        try {
            notifyImpl(name, id, mask, cookie);
        } catch (Throwable e) {
            // Native threads may ignore exceptions; we reraise them here
            // to make sure unhandled errors do indeed propagate.
            SystemUtil.fatal(e);
        }
    }

    /**
     * The actual callback implementation.  This is a separate function from notify() so that it
     * can throw.
     * @param name The filename or foldername under the watched folder to which this event pertains
     * @param id The watch id for which this event was generated
     * @param mask A bitmask of event attributes that describe this event
     * @param cookie A unique number associating paired IN_MOVED_FROM and IN_MOVED_TO events
     * @throws JNotifyException
     * N.B. the parent folder can be retrieved with getWatchPath(id).
     */
    public synchronized void notifyImpl(String name, int id, int mask,
            int cookie) throws JNotifyException
    {
        // Log the event.
        if (Util.test(mask, IN_CREATE)) {
            l.debug("inotify: IN_CREATE " + name + ", " + id + ", " + mask);
        }
        if (Util.test(mask, IN_MODIFY)) {
            l.debug("inotify: IN_MODIFY " + name + ", " + id + ", " + mask);
        }
        if (Util.test(mask, IN_MOVED_TO)) {
            l.debug("inotify: IN_MOVED_TO " + name + ", " + id + ", " + mask);
        }
        if (Util.test(mask, IN_ATTRIB)) {
            l.debug("inotify: IN_ATTRIB " + name + ", " + id + ", " + mask);
        }
        if (Util.test(mask, IN_DELETE)) {
            l.debug("inotify: IN_DELETE " + name + ", " + id + ", " + mask);
        }
        if (Util.test(mask, IN_MOVED_FROM)) {
            l.debug("inotify: IN_MOVED_FROM " + name + ", " + id + ", " + mask);
        }

        // if an ID is present in _deletedAckPending, we have already requested that the
        // kernel stop sending us events associated with this ID (because the folder is no longer
        // present or no longer under the root anchor) and we should ignore any that do arrive.
        if (_deletedAckPending.contains(id)) {
            l.debug("inotify: got event for watch " + id + " pending deletion, ignoring");
        } else {
            // Check if we need to handle an unwatching deferred from a previous event.
            if ( Util.test(_prevActionMask, IN_ISDIR) &&
                    Util.test(_prevActionMask, IN_MOVED_FROM)) {
                // We follow this path if the previous action was a MOVED_FROM on a directory.
                // If the moved folder shows up somewhere else in our watched tree, we should
                // just move it.  Otherwise, we need to recursively remove its watches, since
                // we deferred the watch removal to reduce churn (see below).
                if (Util.test(mask, IN_ISDIR) && Util.test(mask, IN_MOVED_TO) &&
                        _prevCookie == cookie) {
                    // It looks like this event is the matching MOVED_TO for the previous
                    // MOVED_FROM event.  We should just update the path associated with the
                    // appropriate watch.
                    if (l.isDebugEnabled()) {
                        l.debug("Saw a move from " + getWatchPath(_prevActionId) + "/" +
                                _prevName + " to " + getWatchPath(id) + "/" + name);
                    }
                    LinuxINotifyWatch oldParent = _watches.get(_prevActionId);
                    LinuxINotifyWatch newParent = _watches.get(id);
                    // Find the moved watch
                    LinuxINotifyWatch thisWatch = null;
                    for (LinuxINotifyWatch watch : oldParent._children) {
                        if (watch._name.equals(_prevName)) {
                            thisWatch = watch;
                            break;
                        }
                    }
                    if (thisWatch == null) {
                        // There's a race condition where we try to subscribe a watch on a
                        // folder, then on its children, but a move happens before we can place
                        // the child watch.  In this case, thisWatch is null.  We'll add the watch
                        // down below.
                        l.debug("No old watch on " + name + " , guess we lost a race recently.");
                    } else {
                        // Remove the watch from its former parent's child list
                        oldParent._children.remove(thisWatch);
                        // Update the watch's parent id
                        thisWatch._parentWatchId = id;
                        // Update the watch's name
                        thisWatch._name = name;
                        // Add the watch to its new parent's child list
                        newParent._children.add(thisWatch);
                    }
                } else {
                    // The previous event was a MOVED_FROM on a folder, and we did not receive the
                    // matching MOVED_TO.  This means that the folder was moved outside of the
                    // root anchor, and we should (recursively) unwatch the target of the previous
                    // event.
                    LinuxINotifyWatch oldParent = _watches.get(_prevActionId);
                    l.debug("Following up on previous event: removing watches");
                    for(LinuxINotifyWatch watch : oldParent._children) {
                        if (watch._name.equals(_prevName)) {
                            oldParent._children.remove(watch);
                            removeWatchRecursively(watch);
                            break;
                        }
                    }
                }
            }

            // Handle this event.
            if (Util.test(mask, (IN_CREATE | IN_MODIFY | IN_MOVED_TO | IN_ATTRIB)) &&
                    (!name.isEmpty() || _watches.get(id)._parentWatchId != WATCH_ID_ROOT)) {
                // These four cases constitute all instances in which a file was created or updated.
                // However, we should avoid propagating events that apply to the root anchor
                // itself, rather than children of the root anchor.
                File affectedFile = new File(getWatchPath(id), name);

                List<String> addedWatches = Lists.newLinkedList();
                // If the event is for a created or moved folder, we should register a new
                // recursive watch - it might have children we haven't seen yet.
                if (Util.test(mask, IN_ISDIR) && Util.test(mask, (IN_CREATE | IN_MOVED_TO))) {
                    addWatchRecursively(name, id, addedWatches);
                }

                // Here's the terrible, horrible, no good, very bad truth about the Linux notifier:
                // Because we have to manually re-implement recursive watching on top of the severly
                // limited inotify interface we cannot simply post a MCN event for a folder if we
                // added watches under it.
                //
                // If we did, we may would notifications and end up never picking up whole subfolder
                // hierarchies in cases like:
                //
                // Core    : start scan
                // FS      : create dir foo
                // FS      : create dir foo/bar
                // FS      : create stuff under foo/bar...
                // Core    : scan foo -> children={d0}
                // Core    : scan children of foo/d0...
                // FS      : create dir foo/baz
                // Notifier: detect creation of foo, add watches recursively, send MCN for foo
                // Core    : handle MCN for foo -> unchanged since scan -> does not trigger rescan
                //
                // The whole foo/d1 hierarchy is forever out of sync until foo/d1 is renamed or some
                // other event causes a rescan.
                // NB: Such cases are reliably reproduced by syncdet stress tests creating large
                // folder hierarchies.
                //
                // One solution would be to indicate in the MCN that it new watches were added and
                // that even if the scanner already picked up some of the object, chances are it
                // missed some of them and a rescan is needed.
                //
                // Another approach, which happens to be the one we've taken, is to emit a MCN for
                // every watch added to ensure the folders are picked up. This was chosen because
                // it more closely mirrors the behavior of the Windows notifiers and avoids leaking
                // more platform-specific behaviors into the Scanner.
                mightCreate(!addedWatches.isEmpty() ? addedWatches
                        : Collections.singleton(affectedFile.getAbsolutePath()));
            } else if (Util.test(mask, (IN_DELETE | IN_MOVED_FROM ))) {
                // These two cases constitute all instances in which a file or folder disappeared.
                File vanishedFile = new File(getWatchPath(id), name);
                mightDelete(vanishedFile.getAbsolutePath());
                // Ordinarily, we'd want to test mask for IN_MOVED_FROM and IN_ISDIR here, and if
                // the event was a MOVED_FROM on a directory, then we'd want to unwatch it and all
                // of its children which may no longer be under our root anchor.

                // However, in the case of a folder rename or relocation,
                // it's much more efficient (and less prone to dropping filesystem events!) to
                // simply update our watch tree with the appropriate name and parent.
                // Since we can't tell the difference between a move out (IN_MOVED_FROM) and a
                // move within (IN_MOVED_FROM, then IN_MOVED_TO) until the second event arrives,
                // we defer this until the next event is received; otherwise, we'll
                // over-aggressively unwatch and rewatch moved folders (recursively!),
                // which is slow and can lead to lost filesystem events.
            }
        }

        // Handle watch termination.
        // Whenever a kernel watch is removed, either manually (with inotify_rm_watch()) or
        // automatically (file was deleted, or fs unmounted), we will get an inotify event with
        // IN_IGNORED.  This is the kernel's acknowledgement and promise not to send any more
        // events associated with this inode/watch id (unless you inotify_add_watch() it again).
        if (Util.test(mask, IN_IGNORED)) {
            l.debug("inotify: IN_IGNORED watch id " + id + " removed by kernel.");
            LinuxINotifyWatch removedWatch = _watches.get(id);
            // We should never lose a watch we don't think we have.
            assert(removedWatch != null);
            int parentId = removedWatch._parentWatchId;
            if (parentId != WATCH_ID_ROOT) {
                // We may not get the child IN_IGNORED before we get the parent's.  This can
                // happen when the parent is moved outside of the root anchor and we act to cancel
                // the parent's watch, but the child gets deleted by another process before that can
                // transpire.  This gets triggered a lot by the merge testcases.
                LinuxINotifyWatch parentWatch = _watches.get(parentId);
                if (parentWatch != null) {
                    parentWatch._children.remove(removedWatch);
                }
            }
            _watches.remove(id); // This should be the only place where watches are
                                 // actually removed from the map.

            // collection.remove() accepts both int and Object.  id will autobox to either, so
            // we do an explicit cast to Integer to avoid ambiguity - we want to remove the
            // object, not the item at that index.
            _deletedAckPending.remove(Integer.valueOf(id));
        }

        // Handle event queue overflow.
        // If we get a notification with IN_Q_OVERFLOW set, that means we were way too slow at
        // handling file notifications, and the kernel queue overflowed.
        // This means we lost notifications, and could have missed folders that we need to add
        // watches on as well.
        // The only thing we can do to regain a full, accurate state of the world is cancel all
        // watches, re-add the root watch (recursively), and then do a full rescan.
        // We have to do it in this order, or else we may lose events between the watch setup and
        // the rescan.
        // Additionally, we may still get notifications that the worker thread has read into an
        // internal buffer until all the watch cancellations are acknowledged.
        // As a bonus, JNotify is a piece of crap that has no way to stop its internal runLoop
        // thread. This means that if we try to destroy the object, we will leak threads.  And
        // they'll be trying to read file descriptors that may get reused.  FML.
        // TODO: (DF) patch jnotify to stop the runloop cleanly

        // In the meantime, it's easier to just abort the daemon and start over.
        if (Util.test(mask, IN_Q_OVERFLOW)) {
            SystemUtil.fatal(
                    "inotify event queue overflowed; aborting to regain a consistent state");
        }

        _prevActionId = id;
        _prevCookie = cookie;
        _prevActionMask = mask;
        _prevName = name;
    }

    private void mightCreate(Collection<String> paths)
    {
        // TODO: reduce core lock contention by enqueuing the events in a batch.
        // Alternatively create a new batch-create notification with smarter logic
        // to reduce the load on core queue and automatically discard events for
        // children of a path that causes a rescan.
        for (String path : paths) mightCreate(path);
    }

    private void mightCreate(String path)
    {
        if (l.isDebugEnabled()) l.debug("mightCreate(" + ObfuscatingFormatters.obfuscatePath(path) + ")");
        _cq.enqueueBlocking(new EIMightCreateNotification(path), Linker.PRIO);
    }

    private void mightDelete(String path)
    {
        if (l.isDebugEnabled()) l.debug("mightDelete(" + ObfuscatingFormatters.obfuscatePath(path) + ")");
        _cq.enqueueBlocking(new EIMightDeleteNotification(path), Linker.PRIO);
    }

}
