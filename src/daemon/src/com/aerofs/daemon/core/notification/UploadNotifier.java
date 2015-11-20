package com.aerofs.daemon.core.notification;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.lib.Throttler;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.google.inject.Inject;

import javax.annotation.Nullable;

class UploadNotifier extends AbstractTransferNotifier
{
    /** we use a specific type to differentiate the throttler to be created by guice */
    static class UploadThrottler extends Throttler<TransferredItem>
    {
        @Inject
        public UploadThrottler(ElapsedTimer.Factory factTimer)
        {
            super(factTimer);
        }
    }

    private final UploadThrottler _throttler;

    @Inject
    UploadNotifier(DirectoryService ds, UserAndDeviceNames nr, RitualNotificationServer rns,
                   CoreScheduler sched, UploadThrottler throttler)
    {
        super(ds, nr, rns, sched);

        _throttler = throttler;
        _throttler.setDelay(1 * C.SEC);
    }

    @Override
    protected @Nullable PBNotification createTransferNotification_(TransferredItem item, TransferProgress progress, boolean forceNotificationGeneration)
    {
        if ((progress._done < progress._total) && !forceNotificationGeneration && _throttler.shouldThrottle(item)) {
            return null;
        }

        if (progress._done >= progress._total) {
            _throttler.untrack(item);
        }

        return newTransferNotification_(item, progress, true);
    }
}