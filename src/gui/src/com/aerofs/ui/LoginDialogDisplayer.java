/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.lib.Util;
import com.aerofs.proto.ControllerNotifications;
import com.aerofs.ui.IUI.MessageType;
import com.google.protobuf.GeneratedMessageLite;
import org.apache.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class LoginDialogDisplayer
{
    /**
     * This class is responsible for receiving SHOW_LOGIN_NOTIFICATIONS.  It calls login_()
     * on the UI when a notification is received.
     */
    private AtomicBoolean dialogIsOpen = new AtomicBoolean(false);

    private static final Logger l = Util.l(LoginDialogDisplayer.class);

    private final IUINotificationListener _l = new IUINotificationListener()
    {
        @Override
        public void onNotificationReceived(GeneratedMessageLite notification)
        {
            showDialog();
        }
    };

    private void showDialog()
    {
        l.warn("Invalid Credentials");
        // Try to set dialogIsOpen from false to true.  This will only succeed for one
        // thread at a time, guaranteeing only one dialog can be displayed at a time.
        if (dialogIsOpen.compareAndSet(false,true)) {
            UI.get().notify(MessageType.ERROR, "Couldn't Sign in",
                    "You need to update your password.",
                    null);
            try {
                UI.get().login_();
            } catch (Exception e) {
                Util.l(this).warn(e);
            }
            dialogIsOpen.set(false);
        }
    }

    public LoginDialogDisplayer()
    {
        UI.notifier().addListener(ControllerNotifications.Type.SHOW_LOGIN_NOTIFICATION, _l);
    }
}
