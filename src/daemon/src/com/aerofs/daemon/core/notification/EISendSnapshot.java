package com.aerofs.daemon.core.notification;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.protocol.DownloadState;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.State;
import com.aerofs.daemon.core.net.IUploadStateListener.Key;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.lib.pb.PBTransferStateFormatter;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.RitualNotifications.PBNotification;

/**
 * Retrieve the snapshot of all the events from the core
 */
class EISendSnapshot extends AbstractEBSelfHandling
{
    private final RitualNotificationServer _notifier;
    private final InetSocketAddress _to;
    private final DownloadState _dls;
    private final UploadState _uls;
    private final PathStatusNotifier _psn;
    private final PBTransferStateFormatter _formatter;

    EISendSnapshot(RitualNotificationServer notifier, InetSocketAddress to,
            DownloadState dls, UploadState uls, PathStatusNotifier psn,
            PBTransferStateFormatter formatter)
    {
        _notifier = notifier;
        _to = to;
        _dls = dls;
        _uls = uls;
        _psn = psn;
        _formatter = formatter;
    }

    @Override
    public void handle_()
    {
        Map<SOCID, State> dls = _dls.getStates_();
        Map<Key, Value> uls = _uls.getStates_();

        PBNotification pbs[] = new PBNotification[dls.size() + uls.size()];
        int idx = 0;
        for (Entry<SOCID, State> en : dls.entrySet()) {
            pbs[idx++] = _formatter.formatDownloadState(en.getKey(), en.getValue());
        }

        for (Entry<Key, Value> en : uls.entrySet()) {
            pbs[idx++] = _formatter.formatUploadState(en.getKey(), en.getValue());
        }

        _notifier.sendSnapshot_(_to, pbs);
        _psn.sendConflictCount_();
    }
}
