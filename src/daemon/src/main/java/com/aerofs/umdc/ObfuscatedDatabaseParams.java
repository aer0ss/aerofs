/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.umdc;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.IDatabaseParams;

import java.io.File;

//TODO (EK) Merge with CfgCoreDatabaseParams
public class ObfuscatedDatabaseParams implements IDatabaseParams
{
    private String _dbName;

    public ObfuscatedDatabaseParams(String dbName)
    {
        _dbName = dbName;
    }

    @Override
    public String url() {
        return "jdbc:sqlite:" + Cfg.absRTRoot() + File.separator + _dbName;
    }

    @Override
    public boolean sqliteExclusiveLocking()
    {
        return false;
    }

    @Override
    public boolean sqliteWALMode()
    {
        return false;
    }

    @Override
    public boolean autoCommit()
    {
        return true;
    }
}
