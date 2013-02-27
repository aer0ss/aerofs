/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.base.Loggers;
import com.aerofs.sp.client.IBadCredentialListener;
import com.aerofs.proto.ControllerNotifications.Type;
import org.slf4j.Logger;

public class ControllerBadCredentialListener implements IBadCredentialListener
{
    private static final Logger l = Loggers.getLogger(IBadCredentialListener.class);

    @Override
    public void exceptionReceived()
    {
        l.warn("Bad Credential Exception received");
        ControllerService.get().notifyUI(Type.SHOW_RETYPE_PASSWORD_NOTIFICATION, null);
    }
}
