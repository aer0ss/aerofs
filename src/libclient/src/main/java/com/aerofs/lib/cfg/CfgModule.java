package com.aerofs.lib.cfg;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class CfgModule extends AbstractModule
{
    @Override
    protected void configure()
    {
    }

    @Provides
    public CfgDatabase provideCfgDatabase()
    {
        return Cfg.db();
    }
}
