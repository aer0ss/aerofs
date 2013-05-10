package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.lib.pb.PBTransferStateFormatter;
import com.aerofs.lib.KeyBasedThrottler.Factory;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.google.inject.Inject;

class DownloadStateListener extends AbstractTransferStateListener
{
    private final PBTransferStateFormatter _formatter;

    @Inject
    public DownloadStateListener(RitualNotificationServer notifier, Factory factThrottler,
            PBTransferStateFormatter formatter)
    {
        super(notifier, factThrottler);
        _formatter = formatter;
    }

    @Override
    protected PBNotification notificationForState(Key key, Value value)
    {
        return _formatter.formatDownloadState(key, value);
    }
}