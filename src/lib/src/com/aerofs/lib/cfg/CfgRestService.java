/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.cfg;

import com.aerofs.lib.cfg.CfgDatabase.Key;

public class CfgRestService
{
    public boolean isEnabled()
    {
        return Cfg.db().getBoolean(Key.REST_SERVICE, getDefaultValue());
    }

    public boolean getDefaultValue()
    {
        return true;
    }
}
