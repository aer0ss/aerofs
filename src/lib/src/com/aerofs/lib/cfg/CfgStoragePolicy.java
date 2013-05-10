/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.google.inject.Inject;
import org.slf4j.Logger;

/**
 * Parameters for sync history storage.
 */
public class CfgStoragePolicy
{
    /**
     * Indicate whether to use any sync history storage at all.
     * @return true if normal sync history storage policy is in effect
     */
    public boolean useHistory()
    {
        return _useSyncHistoryCached;
    }

    /**
     * Populates cached value with database value on instantiation, and
     * installs a database listener.
     */
    @Inject
    public CfgStoragePolicy(CfgDatabase cfgdb)
    {
        _l = Loggers.getLogger(CfgStoragePolicy.class);
        _cfgdb = cfgdb;
        updateCache();

        _cfgdb.addListener(new ICfgDatabaseListener() {
            @Override
            public void valueChanged_(Key key)
            {
                if (key == Key.SYNC_HISTORY) { updateCache(); }
            }
        });
    }

    private void updateCache()
    {
        _useSyncHistoryCached = _cfgdb.getBoolean(Key.SYNC_HISTORY);
        _l.debug("Enable-sync history value changed to {}", _useSyncHistoryCached);
    }

    private final CfgDatabase _cfgdb;
    private final Logger _l;
    private boolean _useSyncHistoryCached;
}
