package com.aerofs.daemon.lib.db;

import javax.inject.Inject;

import com.aerofs.lib.cfg.CfgCoreDatabaseParams;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;

/**
 * A DI wrapper class for the core DBCW.
 *
 * An alternative approach is to let this class implement IDBCW, and delegate all the interface
 * methods to _dbcw. But that would be dumb.
 *
 */
public class CoreDBCW
{
    private final IDBCW _dbcw;

    @Inject
    public CoreDBCW(CfgCoreDatabaseParams dbParams)
    {
        _dbcw = DBUtil.newDBCW(dbParams);
    }

    public IDBCW get()
    {
        return _dbcw;
    }
}
