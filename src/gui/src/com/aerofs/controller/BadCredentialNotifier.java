/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.proto.ControllerNotifications;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ritual_notification.IRitualNotificationListener;
import com.aerofs.ui.UI;

public class BadCredentialNotifier
{
    public BadCredentialNotifier()
    {
        IRitualNotificationListener l = new IRitualNotificationListener()
        {
            @Override
            public void onNotificationReceived(PBNotification pb)
            {
                if (pb.getType().equals(Type.BAD_CREDENTIAL)) {
                    ControllerService.get().notifyUI(
                            ControllerNotifications.Type.SHOW_RETYPE_PASSWORD_NOTIFICATION, null);
                }
            }

            @Override
            public void onNotificationChannelBroken()
            {
                // noop
            }
        };

        UI.rnc().addListener(l);
    }
}
