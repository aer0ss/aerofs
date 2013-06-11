package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.core.transfers.BaseTransferState;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferProgress;
import com.aerofs.daemon.core.transfers.ITransferStateListener.TransferredItem;
import com.aerofs.daemon.core.transfers.download.DownloadState;
import com.aerofs.daemon.core.transfers.upload.UploadState;
import com.aerofs.lib.event.AbstractEBSelfHandling;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Retrieve the snapshot of all the events from the core
 */
class EISendSnapshot extends AbstractEBSelfHandling
{
    private final DownloadState _dls;
    private final DownloadNotifier _dn;
    private final UploadState _uls;
    private final UploadNotifier _un;
    private final PathStatusNotifier _psn;
    private final boolean _filterMeta;

    EISendSnapshot(DownloadState dls, DownloadNotifier dn, UploadState uls, UploadNotifier un, PathStatusNotifier psn, boolean filterMeta)
    {
        _dls = dls;
        _dn = dn;
        _uls = uls;
        _un = un;
        _psn = psn;
        _filterMeta = filterMeta;
    }

    private boolean ignoreTransfer_(TransferredItem item)
    {
        return _filterMeta && item._socid.cid().isMeta();
    }

    private void sendTransferNotifications_(BaseTransferState transferState, AbstractTransferNotifier transferNotifier)
    {
        Map<TransferredItem, TransferProgress> transfers = transferState.getStates_();

        for (Entry<TransferredItem, TransferProgress> transfer : transfers.entrySet()) {
            if (!ignoreTransfer_(transfer.getKey())) {
                transferNotifier.sendTransferNotification_(transfer.getKey(), transfer.getValue());
            }
        }
    }

    private void sendPathStatusNotifications_()
    {
        _psn.sendConflictCountNotification_();
    }

    @Override
    public void handle_()
    {
        sendTransferNotifications_(_dls, _dn);
        sendTransferNotifications_(_uls, _un);
        sendPathStatusNotifications_();
    }
}
