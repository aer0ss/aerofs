/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.update.uput;

import com.aerofs.lib.cfg.Cfg;

import static com.aerofs.lib.cfg.ICfgStore.CONTACT_EMAIL;

public class UPUTSetContactEmail implements IUIPostUpdateTask
{
    @Override
    public void run()
            throws Exception
    {
        Cfg.db().set(CONTACT_EMAIL, Cfg.user().getString());
    }
}
