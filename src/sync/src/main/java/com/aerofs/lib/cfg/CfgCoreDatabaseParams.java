package com.aerofs.lib.cfg;

import com.aerofs.lib.ClientParam;
import com.aerofs.lib.db.IDatabaseParams;

import java.io.File;

/**
 * Parameters for the core database
 */
public class CfgCoreDatabaseParams implements IDatabaseParams
{
    @Override
    public String url()
    {
        return "jdbc:sqlite:" + BaseCfg.getInstance().absRTRoot() + File.separator
                + ClientParam.CORE_DATABASE;
    }

    @Override
    public boolean sqliteExclusiveLocking()
    {
        return true;
    }

    @Override
    public boolean sqliteWALMode()
    {
        return true;
    }

    @Override
    public boolean autoCommit()
    {
        return false;
    }
}
