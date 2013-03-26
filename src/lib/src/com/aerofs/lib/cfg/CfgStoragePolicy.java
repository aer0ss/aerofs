/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

/**
 * Parameters for version history storage.
 * NOTE: This should be moved into CfgDatabase, and add listeners to Linked and Block storage
 * types. Temporary for simplest-possible-implementation.
 */
public class CfgStoragePolicy
{
    /**
     * Indicate whether to use any version history storage at all.
     * @return true if normal version history storage policy is in effect
     */
    public boolean useHistory()
    {
        return Cfg.useHistory();
    }
}
