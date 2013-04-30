package com.aerofs.daemon.core.notification;

import com.aerofs.base.C;
import com.aerofs.daemon.core.protocol.IDownloadStateListener;
import com.aerofs.daemon.lib.pb.PBTransferStateFormatter;
import com.aerofs.lib.KeyBasedThrottler;
import com.aerofs.lib.KeyBasedThrottler.Factory;
import com.aerofs.lib.id.SOCID;
import com.google.inject.Inject;

class DownloadStateListener implements IDownloadStateListener
{
    private final RitualNotificationServer _notifier;
    private final PBTransferStateFormatter _formatter;
    private final Factory _factory;

    private KeyBasedThrottler<SOCID> _throttler;
    private boolean _enableFilter = true;

    @Inject
    DownloadStateListener(RitualNotificationServer notifier, PBTransferStateFormatter formatter,
            Factory factory)
    {
        _notifier = notifier;
        _formatter = formatter;
        _factory = factory;
    }

    public void enableFilter(boolean enable)
    {
        _enableFilter = enable;
    }

    @Override
    public void stateChanged_(SOCID socid, State state)
    {
        if (_throttler == null) {
            _throttler = _factory.<SOCID>create();
            _throttler.setDelay(1 * C.SEC);
        }

        if (_enableFilter && socid.cid().isMeta()) return;

        if (state instanceof Ongoing) {
            if (_throttler.shouldThrottle(socid)) return;
        } else if (state instanceof Ended) {
            _throttler.untrack(socid);
        } else if (_enableFilter) return;

        _notifier.sendEvent_(_formatter.formatDownloadState(socid, state));
    }
}
