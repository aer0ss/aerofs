/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.google.inject.Inject;

import static com.aerofs.lib.cfg.ICfgStore.SYNC_HISTORY;

/**
 * Parameters for sync history storage.
 */
public class CfgStoragePolicy
{
    private ICfgStore _cfgStore;

    /**
     * Indicate whether to use any sync history storage at all.
     * @return true if normal sync history storage policy is in effect
     */
    public boolean useHistory()
    {
        return _cfgStore.getBoolean(SYNC_HISTORY);
    }

    /**
     * Populates cached value with database value on instantiation, and
     * installs a database listener.
     */
    @Inject
    public CfgStoragePolicy(ICfgStore cfgStore)
    {
        _cfgStore = cfgStore;
    }
}
