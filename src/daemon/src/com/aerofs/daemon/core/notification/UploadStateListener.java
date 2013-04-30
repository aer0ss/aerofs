package com.aerofs.daemon.core.notification;

import com.aerofs.base.C;
import com.aerofs.daemon.core.net.IUploadStateListener;
import com.aerofs.daemon.lib.pb.PBTransferStateFormatter;
import com.aerofs.lib.KeyBasedThrottler;
import com.aerofs.lib.KeyBasedThrottler.Factory;
import com.google.inject.Inject;

class UploadStateListener implements IUploadStateListener
{
    private final RitualNotificationServer _notifier;
    private final PBTransferStateFormatter _formatter;
    private final Factory _factory;

    private KeyBasedThrottler<Key> _throttler;
    private boolean _enableFilter = true;

    @Inject
    UploadStateListener(RitualNotificationServer notifier, PBTransferStateFormatter formatter,
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
    public void stateChanged_(Key key, Value value)
    {
        if (_throttler == null) {
            _throttler = _factory.<Key>create();
            _throttler.setDelay(1 * C.SEC);
        }

        if (_enableFilter && key._socid.cid().isMeta()) return;

        if (value._done < value._total) {
            if (_throttler.shouldThrottle(key)) return;
        } else {
            _throttler.untrack(key);
        }

        _notifier.sendEvent_(_formatter.formatUploadState(key, value));
    }
}
