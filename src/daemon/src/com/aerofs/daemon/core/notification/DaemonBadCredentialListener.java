/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.notification;

import com.aerofs.lib.Util;
import com.aerofs.sp.client.IBadCredentialListener;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import org.slf4j.Logger;

public class DaemonBadCredentialListener implements IBadCredentialListener
{
    private static final Logger l = Util.l(DaemonBadCredentialListener.class);
    private final RitualNotificationServer _notifier;

    DaemonBadCredentialListener(RitualNotificationServer notifier)
    {
        _notifier = notifier;
    }

    @Override
    public void exceptionReceived()
    {
        l.warn("Bad Credential Exception received");
        PBNotification pbn = PBNotification.newBuilder().setType(Type.BAD_CREDENTIAL).build();
        _notifier.sendEvent_(pbn);
    }
}
