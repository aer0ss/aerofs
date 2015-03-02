package com.aerofs.fsck;

import com.aerofs.lib.cfg.CfgCoreDatabaseParams;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.internal.Scoping;

public class FSCKModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(Scoping.class).toInstance(Scoping.SINGLETON_INSTANCE);
    }

    @Provides @Singleton
    public IDBCW provideIDBCW(CfgCoreDatabaseParams dbParams)
    {
        return DBUtil.newDBCW(dbParams);
    }
}
