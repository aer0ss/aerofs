package com.aerofs.daemon.core.linker.notifier.osx;

import java.util.LinkedHashSet;
import java.util.List;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.linker.Linker;
import com.aerofs.daemon.core.linker.notifier.INotifier;
import com.aerofs.daemon.core.linker.scanner.ScanSessionQueue;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.lib.Path;
import com.aerofs.lib.cfg.CfgAbsRootAnchor;
import com.aerofs.lib.injectable.InjectableJNotify;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import net.contentobjects.jnotify.JNotifyException;
import net.contentobjects.jnotify.macosx.FSEventListener;

public class OSXNotifier implements INotifier, FSEventListener
{
    private final CoreQueue _cq;
    private final ScanSessionQueue _ssq;
    private final InjectableJNotify _jn;
    private final CfgAbsRootAnchor _cfgAbsRootAnchor;

    // we use linked hash sets to 1) specify the order of the folders to be scanned, as
    // described in ScanSession's constructor, and 2) optimize set iteration by ScanSession.
    private LinkedHashSet<String> _batch;

    // for simplicity we maintain one recurse flag for all entries in one batch
    private boolean _recurse;

    // the watch id
    private int _id;

    public OSXNotifier(ScanSessionQueue ssq, CoreQueue cq, InjectableJNotify jn,
            CfgAbsRootAnchor cfgAbsRootAnchor)
    {
        _cq = cq;
        _ssq = ssq;
        _jn = jn;
        _cfgAbsRootAnchor = cfgAbsRootAnchor;
    }

    @Override
    public void start_() throws JNotifyException
    {
        _jn.macosx_setNotifyListener(this);
        _id = _jn.macosx_addWatch(_cfgAbsRootAnchor.get());
    }

    @Override
    public void batchStart(int id)
    {
        assert id == _id;
        assert _batch == null;

        _batch = Sets.newLinkedHashSet();
    }

    @Override
    public void notifyChange(int id, String root, String name, boolean recurse)
    {
        assert id == _id;
        assert name.length() > root.length();

        _batch.add(name);
        if (recurse) _recurse = true;
    }

    @Override
    public synchronized void batchEnd(int id)
    {
        assert id == _id;
        assert _batch != null;
        assert !_batch.isEmpty();

        final LinkedHashSet<String> batch = pruneBatch(_batch);
        final boolean recurse = _recurse;
        _cq.enqueueBlocking(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                _ssq.scanImmediately_(batch, recurse);
            }
        }, Linker.PRIO);

        // for debugging only
        _batch = null;
    }

    /**
     * Removes all the paths under {@code batch} that are children of other paths to avoid
     * rescanning the same folder multiple times.
     *
     * For example: if batch is [/abc/ade/efg, /abc/cde, /bcd/afg/epg, /bcd, /bcd/abc] then
     * prunedBatch is [/abc/ade/efg, /abc/cde, /bcd].
     *
     * Runtime: O(n^2) respectively n(n+1)/2, however expected to perform better due to the break.
     * Memory: O(n)
     */
    private LinkedHashSet<String> pruneBatch(LinkedHashSet<String> batch)
    {
        LinkedHashSet<String> prunedPaths = Sets.newLinkedHashSet();

        for (String batchPath : batch) {
            boolean isCovered = false; // is the batch path under pruned path
            boolean isParent = false; // is the batch path a parent of pruned path
            List<String> longerPaths = Lists.newArrayList();

            for (String prunedPath : prunedPaths) {
                if (Path.isUnder(prunedPath, batchPath)) {
                    isCovered = true;
                    break;
                }
                if (Path.isUnder(batchPath, prunedPath)) {
                    isParent = true;
                    longerPaths.add(prunedPath);
                }
            }

            if (!isCovered && !isParent) {
                prunedPaths.add(batchPath);
            } else if (isParent) {
                prunedPaths.removeAll(longerPaths);
                prunedPaths.add(batchPath);
            }
        }

        return prunedPaths;
    }
}
