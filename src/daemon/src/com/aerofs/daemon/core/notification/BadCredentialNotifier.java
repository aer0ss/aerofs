/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.base.Loggers;
import com.aerofs.ritual_notification.RitualNotificationServer;
import com.aerofs.sp.client.IBadCredentialListener;
import com.google.inject.Inject;
import org.slf4j.Logger;

import static com.aerofs.daemon.core.notification.Notifications.newBadCredentialReceivedNotification;

class BadCredentialNotifier implements IBadCredentialListener
{
    private static final Logger l = Loggers.getLogger(BadCredentialNotifier.class);

    private final RitualNotificationServer _ritualNotificationServer;

    @Inject
    BadCredentialNotifier(RitualNotificationServer ritualNotificationServer)
    {
        this._ritualNotificationServer = ritualNotificationServer;
    }

    @Override
    public void exceptionReceived()
    {
        l.warn("fail sp login: bad credentials");
        _ritualNotificationServer.getRitualNotifier().sendNotification(newBadCredentialReceivedNotification());
    }
}
