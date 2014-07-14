package com.aerofs.daemon.core.phy.linked.linker.notifier.osx;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.phy.linked.linker.Linker;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.notifier.INotifier;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.injectable.InjectableJNotify;
import com.aerofs.lib.os.OSUtilOSX;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.macosx.FSEventListener;
import net.contentobjects.jnotify.macosx.JNotify_macosx;

import java.util.LinkedHashSet;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class OSXNotifier implements INotifier, FSEventListener
{
    private final CoreQueue _cq;
    private final InjectableJNotify _jn;

    private static class Batch
    {
        private final LinkerRoot _root;

        // for simplicity we maintain one recurse flag for all entries in one batch
        private boolean _recurse;

        // we use linked hash sets to 1) specify the order of the folders to be scanned, as
        // described in ScanSession's constructor, and 2) optimize set iteration by ScanSession.
        LinkedHashSet<String> _batch;

        Batch(LinkerRoot root) { _root = root; }
    }

    private Map<Integer, Batch> _id2batch = Maps.newConcurrentMap();

    public OSXNotifier(CoreQueue cq, InjectableJNotify jn)
    {
        _cq = cq;
        _jn = jn;
    }

    @Override
    public void start_() throws JNotifyException
    {
        _jn.macosx_setNotifyListener(this);
    }

    @Override
    public int addRootWatch_(LinkerRoot root) throws JNotifyException
    {
        int id = _jn.macosx_addWatch(root.absRootAnchor());
        _id2batch.put(id, new Batch(root));
        return id;

    }

    @Override
    public void removeRootWatch_(LinkerRoot root) throws JNotifyException
    {
        int id = root.watchId();
        _jn.macosx_removeWatch(id);
        _id2batch.remove(id);
    }

    @Override
    public void batchStart(int id)
    {
        Batch b = _id2batch.get(id);
        // avoid race condition between notification and root removal
        if (b == null) return;
        checkState(b._batch == null);

        // We need to recreate a new linked hash set to avoid race conditions
        // as the thread will try to modify the existing hash set.
        b._batch = Sets.newLinkedHashSet();
    }

    @Override
    public void notifyChange(int id, String name, int flags)
    {
        Batch b = _id2batch.get(id);
        // avoid race condition between notification and root removal
        if (b == null) return;

        // FSEvents docs suggest we might in some cases get "/"
        // it's not clear whether that's just a badly written doc or an
        // actual (retarded) behavior but let's be safe...
        if (name.length() < b._root.absRootAnchor().length()) {
            name = b._root.absRootAnchor();
        }

        if (Linker.isInternalPath(name)) return;

        name = OSUtilOSX.normalizeOSXInputFilename(name);
        b._batch.add(name);
        b._recurse |= (flags & JNotify_macosx.MUST_SCAN_SUBDIRS) != 0;
    }

    @Override
    public void batchEnd(int id)
    {
        Batch b = _id2batch.get(id);
        // avoid race condition between notification and root removal
        if (b == null) return;
        checkNotNull(b._batch);
        checkState(!b._batch.isEmpty());

        // Due to the concurrent execution of the notifier thread
        // we need these variables to be final, in case _batch and _recurse gets changed.
        final LinkedHashSet<String> batch = b._batch;
        final boolean recurse = b._recurse;
        final LinkerRoot root = b._root;

        _cq.enqueueBlocking(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                root.scanImmediately_(batch, recurse);
            }
        }, Linker.PRIO);

        // for debugging only
        b._batch = null;
    }
}
