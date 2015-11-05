/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.cfg;

import com.google.inject.Inject;

import static com.aerofs.lib.cfg.CfgDatabase.REST_SERVICE;

public class CfgRestService
{
    private ICfgStore _cfgStore;

    @Inject
    public CfgRestService(ICfgStore cfgStore)
    {
        _cfgStore = cfgStore;
    }

    public boolean isEnabled()
    {
        return _cfgStore.getBoolean(REST_SERVICE);
    }
}
