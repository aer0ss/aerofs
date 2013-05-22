/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers;

import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;

import java.io.PrintStream;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.collect.Maps.newHashMap;

public class BaseTransferState extends ConcurrentlyModifiableListeners<ITransferStateListener> implements IDumpStatMisc
{
    private final Map<TransferredItem, TransferProgress> _state = newHashMap();

    public Map<TransferredItem, TransferProgress> getStates_()
    {
        return _state;
    }

    private void notifyListeners_(TransferredItem item, TransferProgress progress)
    {
        try {
            for (ITransferStateListener l : beginIterating_()) {
                l.onTransferStateChanged_(item, progress);
            }
        } finally {
            endIterating_();
        }
    }

    // @param done == total means completion, either failure or success
    public void progress_(SOCID socid, Endpoint ep, long done, long total)
    {
        TransferredItem item = new TransferredItem(socid, ep);
        TransferProgress progress = new TransferProgress(done, total);
        if (done != total) {
            _state.put(item, progress);
        } else {
            _state.remove(item);
        }

        notifyListeners_(item, progress);
    }

    public void ended_(SOCID socid, Endpoint ep, boolean failed)
    {
        TransferredItem item = new TransferredItem(socid, ep);
        TransferProgress progress = new TransferProgress(failed);
        _state.remove(item);

        notifyListeners_(item, progress);
    }

    @Override
    public void dumpStatMisc(String indent2, String indentUnit, PrintStream ps)
    {
        for (Entry<TransferredItem, TransferProgress> en : getStates_().entrySet()) {
            long done = en.getValue()._done;
            long total = en.getValue()._total;
            long percent = total == 0 ? 0 : (done * 100 / total);
            ps.println(indent2 + en.getKey()._socid + " -> " + en.getKey()._ep + ' ' + String.format(".%1$02d ", percent) + done + '/' + total);
        }
    }
}