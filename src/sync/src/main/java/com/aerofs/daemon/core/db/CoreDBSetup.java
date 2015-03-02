/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.db;

import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * Class in charge of setting up the daemon database
 *
 * The actual DB is made of a collection of schemas (which implement the ISchema interface)
 * registered from Guice Modules through multibinding
 */
public class CoreDBSetup
{
    private final IDBCW _idbcw;
    private final Set<ISchema> _schemas;

    @Inject
    public CoreDBSetup(IDBCW dbcw, Set<ISchema> schemas)
    {
        _idbcw = dbcw;
        _schemas = schemas;
    }

    /**
     * Called on startup, immediately after initializing the DBCW.
     *
     * Should perform initial database setup if needed
     */
    public void setup_() throws SQLException
    {
        // To avoid partial DB setup it is very important that autocommit be disabled
        // To prevent schemas from committing their changes, we do not pass the Connection object
        // directly but a Statement instead.
        Connection c = _idbcw.getConnection();
        assert !c.getAutoCommit();

        // setup all DB schemas, in no particular order
        try (Statement stmt = c.createStatement()) {
            for (ISchema s : _schemas) s.create_(stmt, _idbcw);
        }

        // commit changes after setup complete
        c.commit();
    }

    /**
     * @return true if the core DB has been setup
     *
     * For simplicity and backwards compatibility we simply check for the
     * presence of the table used for {@link TamperingDetection}
     */
    public boolean isSetupDone_() throws SQLException
    {
        return _idbcw.tableExists(TamperingDetectionSchema.T_DBFILE);
    }
}
