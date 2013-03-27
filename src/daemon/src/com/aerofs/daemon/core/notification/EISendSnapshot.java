package com.aerofs.daemon.core.notification;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.protocol.DownloadState;
import com.aerofs.daemon.core.protocol.IDownloadStateListener.State;
import com.aerofs.daemon.core.net.IUploadStateListener.Key;
import com.aerofs.daemon.core.net.IUploadStateListener.Value;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.UserAndDeviceNames;
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
    private final DirectoryService _ds;
    private final TC _tc;
    private final UserAndDeviceNames _nr;

    EISendSnapshot(TC tc, RitualNotificationServer notifier, InetSocketAddress to,
            DownloadState dls, UploadState uls, PathStatusNotifier psn, DirectoryService ds,
            UserAndDeviceNames nr)
    {
        _notifier = notifier;
        _to = to;
        _dls = dls;
        _uls = uls;
        _psn = psn;
        _ds = ds;
        _tc = tc;
        _nr = nr;
    }

    @Override
    public void handle_()
    {
        Map<SOCID, State> dls = _dls.getStates_();
        Map<Key, Value> uls = _uls.getStates_();

        PBNotification pbs[] = new PBNotification[dls.size() + uls.size()];
        int idx = 0;
        for (Entry<SOCID, State> en : dls.entrySet()) {
            pbs[idx++] = DownloadStateListener.state2pb_(_tc, _ds, _nr, en.getKey(), en.getValue());
        }

        for (Entry<Key, Value> en : uls.entrySet()) {
            pbs[idx++] = UploadStateListener.state2pb_(_tc, _ds, _nr, en.getKey(), en.getValue());
        }

        _notifier.sendSnapshot_(_to, pbs);
        _psn.sendConflictCount_();
    }

}
