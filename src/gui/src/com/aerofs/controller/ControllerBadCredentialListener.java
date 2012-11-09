/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.lib.Util;
import com.aerofs.sp.client.IBadCredentialListener;
import com.aerofs.proto.ControllerNotifications.Type;
import org.apache.log4j.Logger;

public class ControllerBadCredentialListener implements IBadCredentialListener
{
    private static final Logger l = Util.l(IBadCredentialListener.class);

    @Override
    public void exceptionReceived()
    {
        l.warn("Bad Credential Exception received");
        ControllerService.get().notifyUI(Type.SHOW_LOGIN_NOTIFICATION, null);
    }
}
