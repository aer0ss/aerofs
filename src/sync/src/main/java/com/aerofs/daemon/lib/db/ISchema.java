/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.lib.db.dbcw.IDBCW;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base interface for DB schema setup
 */
public interface ISchema
{
    /**
     * Setup the DB schema on first startup
     */
    void create_(Statement s, IDBCW dbcw) throws SQLException;

    /**
     * Dumps the contents of the database (only tables created by that schema)
     */
    void dump_(Statement s, PrintStream pw) throws IOException, SQLException;
}
