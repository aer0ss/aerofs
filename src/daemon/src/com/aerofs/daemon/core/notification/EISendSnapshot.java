package com.aerofs.daemon.core.notification;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.aerofs.daemon.core.download.DownloadState;
import com.aerofs.daemon.core.net.ITransferStateListener.Key;
import com.aerofs.daemon.core.net.ITransferStateListener.Value;
import com.aerofs.daemon.core.net.UploadState;
import com.aerofs.daemon.lib.pb.PBTransferStateFormatter;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.google.common.collect.Lists;

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

    private boolean _enableFilter;

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

    public void enableFilter(boolean enable)
    {
        _enableFilter = enable;
    }

    @Override
    public void handle_()
    {
        Map<Key, Value> dls = _dls.getStates_();
        Map<Key, Value> uls = _uls.getStates_();

        // allocate sufficient amount of capacity so we'll never have to grow
        List<PBNotification> pbs = Lists.newArrayListWithCapacity(dls.size() + uls.size());

        for (Entry<Key, Value> en : dls.entrySet()) {
            Key key = en.getKey();

            if (_enableFilter && key._socid.cid().isMeta()) continue;

            pbs.add(_formatter.formatDownloadState(key, en.getValue()));
        }

        for (Entry<Key, Value> en : uls.entrySet()) {
            Key key = en.getKey();

            if (_enableFilter && key._socid.cid().isMeta()) continue;

            pbs.add(_formatter.formatUploadState(key, en.getValue()));
        }

        _notifier.sendSnapshot_(_to, pbs.toArray(new PBNotification[pbs.size()]));
        _psn.sendConflictCount_();
    }
}
