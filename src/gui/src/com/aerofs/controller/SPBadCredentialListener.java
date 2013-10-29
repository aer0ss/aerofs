/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.base.Loggers;
import com.aerofs.sp.client.IBadCredentialListener;
import com.aerofs.ui.UIGlobals;
import org.slf4j.Logger;

public class SPBadCredentialListener implements IBadCredentialListener
{
    private static final Logger l = Loggers.getLogger(IBadCredentialListener.class);

    @Override
    public void exceptionReceived()
    {
        l.warn("Bad Credential Exception received");
        UIGlobals.notifier().notify(IViewNotifier.Type.SHOW_RETYPE_PASSWORD, null);
    }
}
