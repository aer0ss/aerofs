/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.ui.update.uput;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;

public class UPUTSetContactEmail implements IUIPostUpdateTask
{
    @Override
    public void run()
            throws Exception
    {
        Cfg.db().set(Key.CONTACT_EMAIL, Cfg.user().getString());
    }
}
