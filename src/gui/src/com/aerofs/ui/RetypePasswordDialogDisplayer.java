/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.lib.Util;
import com.aerofs.proto.ControllerNotifications;
import com.google.protobuf.GeneratedMessageLite;

import java.util.concurrent.atomic.AtomicBoolean;

public class RetypePasswordDialogDisplayer
{
    /**
     * This class is responsible for receiving SHOW_LOGIN_NOTIFICATIONS.  It calls login()
     * on the UI when a notification is received.
     */
    private AtomicBoolean dialogIsOpen = new AtomicBoolean(false);

    public RetypePasswordDialogDisplayer()
    {
        IUINotificationListener l = new IUINotificationListener()
        {
            @Override
            public void onNotificationReceived(GeneratedMessageLite notification)
            {
                showDialog();
            }
        };
        UI.notifier().addListener(ControllerNotifications.Type.SHOW_RETYPE_PASSWORD_NOTIFICATION, l);
    }

    private void showDialog()
    {
        Util.l(this).warn("Retype password");
        // Try to set dialogIsOpen from false to true.  This will only succeed for one
        // thread at a time, guaranteeing only one dialog can be displayed at a time.
        if (dialogIsOpen.compareAndSet(false, true)) {
            try {
                UI.get().retypePassword();
            } catch (Exception e) {
                Util.l(this).warn(e);
            }
            dialogIsOpen.set(false);
        }
    }
}
