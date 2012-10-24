package com.aerofs.lib.cfg;

import com.aerofs.lib.cfg.CfgDatabase.Key;

public interface ICfgDatabaseListener
{
    /**
     * N.B. False positives are possible.
     */
    void valueChanged_(Key key);
}
