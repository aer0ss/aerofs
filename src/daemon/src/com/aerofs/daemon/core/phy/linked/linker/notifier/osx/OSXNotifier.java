package com.aerofs.daemon.core.phy.linked.linker.notifier.osx;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.LinkedHashSet;
import java.util.Map;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.phy.linked.linker.Linker;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.notifier.INotifier;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.injectable.InjectableJNotify;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.macosx.FSEventListener;

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
        assert b != null;
        assert b._batch == null;

        // We need to recreate a new linked hash set to avoid race conditions
        // as the thread will try to modify the existing hash set.
        b._batch = Sets.newLinkedHashSet();
    }

    @Override
    public void notifyChange(int id, String root, String name, boolean recurse)
    {
        Batch b = _id2batch.get(id);
        assert b != null;
        assert name.length() > root.length();

        if (Linker.isInternalPath(name)) return;

        // OSX uses a variant of Normal Form D therefore @param{name} can be in NFD.
        // @see{http://developer.apple.com/library/mac/#qa/qa1173/_index.html}
        // however the paths stored in the MetaDatabase are in Normal Form C.
        // We have to normalize our NFD path to NFC to avoid the situation where
        // we are looking up a given NFD path in the DS, but it is stored as NFC.
        if (!Normalizer.isNormalized(name, Form.NFC)) {
            name = Normalizer.normalize(name, Form.NFC);
        }
        b._batch.add(name);
        if (recurse) b._recurse = true;
    }

    @Override
    public synchronized void batchEnd(int id)
    {
        Batch b = _id2batch.get(id);
        assert b != null;
        assert b._batch != null;
        assert !b._batch.isEmpty();

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
