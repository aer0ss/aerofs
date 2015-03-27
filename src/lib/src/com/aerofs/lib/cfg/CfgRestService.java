/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.cfg;

import static com.aerofs.lib.cfg.CfgDatabase.REST_SERVICE;

public class CfgRestService
{
    public boolean isEnabled()
    {
        return Cfg.db().getBoolean(REST_SERVICE, getDefaultValue());
    }

    public boolean getDefaultValue()
    {
        return true;
    }
}
