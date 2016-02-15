/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.lib.db.dbcw.IDBCW;

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
}
