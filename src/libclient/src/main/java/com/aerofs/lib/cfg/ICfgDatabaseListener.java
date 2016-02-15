package com.aerofs.lib.cfg;

public interface ICfgDatabaseListener
{
    /**
     * N.B. False positives are possible.
     */
    void valueChanged_(CfgKey key);
}
