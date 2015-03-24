package com.aerofs.daemon.core.db;

import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database schema used by {@link TamperingDetection}
 */
public class TamperingDetectionSchema implements ISchema
{
    public static final String
            T_DBFILE        = "dbfile",
            C_DBFILE_FID    = "dbfile_fid"
            ;

    @Override
    public void create_(Statement s, IDBCW dbcw)  throws SQLException
    {
        s.executeUpdate("create table if not exists " + T_DBFILE + "("
                + C_DBFILE_FID + " blob not null"
                + ")");
    }

    @Override
    public void dump_(Statement s, PrintStream pw) throws IOException, SQLException
    {
    }
}
