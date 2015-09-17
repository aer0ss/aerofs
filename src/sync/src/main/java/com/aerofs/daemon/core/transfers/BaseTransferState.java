/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers;

import com.aerofs.base.BaseUtil;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.IDiagnosable;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;
import com.aerofs.proto.Diagnostics.FileTransfer;
import com.aerofs.proto.Diagnostics.FileTransferDiagnostics;
import com.aerofs.proto.Diagnostics.TransferredObject;

import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.collect.Maps.newHashMap;

public class BaseTransferState extends ConcurrentlyModifiableListeners<ITransferStateListener> implements IDiagnosable
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
        TC.assertHoldsCoreLock_();
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
        TC.assertHoldsCoreLock_();
        TransferredItem item = new TransferredItem(socid, ep);
        TransferProgress progress = new TransferProgress(failed);
        _state.remove(item);

        notifyListeners_(item, progress);
    }

    @Override
    public FileTransferDiagnostics dumpDiagnostics_()
    {
        FileTransferDiagnostics.Builder builder = FileTransferDiagnostics.newBuilder();

        for (Entry<TransferredItem, TransferProgress> en : getStates_().entrySet()) {
            FileTransfer.Builder transferBuilder = FileTransfer.newBuilder();

            SOCID socid = en.getKey()._socid;

            TransferredObject.Builder objectBuilder = TransferredObject.newBuilder();
            objectBuilder.setStoreIndex(socid.sidx().getInt());
            objectBuilder.setOid(BaseUtil.toPB(socid.oid()));
            objectBuilder.setComponentIndex(socid.cid().getInt());

            Endpoint ep = en.getKey()._ep;
            long done = en.getValue()._done;
            long total = en.getValue()._total;
            long percent = total == 0 ? 0 : (done * 100 / total);

            transferBuilder.setObject(objectBuilder);
            transferBuilder.setDid(BaseUtil.toPB(ep.did()));
            transferBuilder.setUsingTransportId(ep.tp().id());
            transferBuilder.setBytesCompleted(done);
            transferBuilder.setTotalBytes(total);
            transferBuilder.setPercentCompleted(percent);

            builder.addTransfer(transferBuilder);
        }

        return builder.build();
    }
}