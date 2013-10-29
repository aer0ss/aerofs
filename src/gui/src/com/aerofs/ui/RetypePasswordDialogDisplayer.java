/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.base.Loggers;
import com.aerofs.controller.IViewNotifier.Type;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class RetypePasswordDialogDisplayer
{
    private static final Logger l = Loggers.getLogger(RetypePasswordDialogDisplayer.class);

    /**
     * This class is responsible for receiving SHOW_LOGIN_NOTIFICATIONS.  It calls login()
     * on the UI when a notification is received.
     */
    private AtomicBoolean dialogIsOpen = new AtomicBoolean(false);

    public RetypePasswordDialogDisplayer()
    {
        IUINotificationListener l = new IUINotificationListener() {
            @Override
            public void onNotificationReceived(Object notification)
            {
                showDialog();
            }
        };
        UIGlobals.notifier().addListener(Type.SHOW_RETYPE_PASSWORD, l);
    }

    private void showDialog()
    {
        l.warn("Retype password");
        // Try to set dialogIsOpen from false to true.  This will only succeed for one
        // thread at a time, guaranteeing only one dialog can be displayed at a time.
        if (dialogIsOpen.compareAndSet(false, true)) {
            try {
                UI.get().retypePassword();
            } catch (Exception e) {
                l.warn("password err:", e);
            }
            dialogIsOpen.set(false);
        }
    }
}
