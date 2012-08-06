package com.aerofs.daemon.core.notification;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DownloadState;
import com.aerofs.daemon.core.net.IDownloadStateListener.State;
import com.aerofs.daemon.core.net.IUploadStateListener.Key;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.lib.id.SOCKID;
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
    private final DirectoryService _ds;
    private final TC _tc;

    EISendSnapshot(TC tc, RitualNotificationServer notifier, InetSocketAddress to,
            DownloadState dls, UploadState uls, DirectoryService ds)
    {
        _notifier = notifier;
        _to = to;
        _dls = dls;
        _uls = uls;
        _ds = ds;
        _tc = tc;
    }

    @Override
    public void handle_()
    {
        Map<SOCKID, State> dls = _dls.getStates_();
        Map<Key, Value> uls = _uls.getStates_();

        PBNotification pbs[] = new PBNotification[dls.size() + uls.size()];
        int idx = 0;
        for (Entry<SOCKID, State> en : dls.entrySet()) {
            pbs[idx++] = DownloadStateListener.state2pb_(_tc, _ds, en.getKey(), en.getValue());
        }

        for (Entry<Key, Value> en : uls.entrySet()) {
            pbs[idx++] = UploadStateListener.state2pb_(_tc, _ds, en.getKey(), en.getValue());
        }

        _notifier.sendSnapshot_(_to, pbs);
    }

}
