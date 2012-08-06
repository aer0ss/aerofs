/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.proto.ControllerNotifications;
import com.aerofs.proto.RitualNotifications.PBNotification;
import com.aerofs.proto.RitualNotifications.PBNotification.Type;
import com.aerofs.ui.RitualNotificationClient.IListener;
import com.aerofs.ui.UI;

public class BadCredentialNotificationManager
{
    private final IListener _l = new IListener()
    {
        @Override
        public void onNotificationReceived(PBNotification pb)
        {
            if (pb.getType().equals(Type.BAD_CREDENTIAL)) {
                ControllerService.get().notifyUI(
                        ControllerNotifications.Type.SHOW_LOGIN_NOTIFICATION, null);
            }
        }
    };

    public BadCredentialNotificationManager()
    {
        UI.rnc().addListener(_l);
    }
}
