package com.aerofs.srvlib.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Class needed to facilitate database transactions.
 */
public abstract class AbstractDatabase
{
    protected Connection _c;

    protected void init_(Connection c)
    {
        this._c = c;
    }

    synchronized void setAutoCommit(boolean state)
            throws SQLException
    {
        assert _c != null : "must call init before set auto commit";
        _c.setAutoCommit(state);
    }

    /**
     * Lock the database class before using this!
     */
    synchronized void commit()
            throws SQLException
    {
        assert _c != null : "must call init before commit";
        _c.commit();
    }

    /**
     * Lock the database class before using this!
     */
    synchronized void rollback()
            throws SQLException
    {
        assert _c != null : "must call init before rollback";
        _c.rollback();
    }
}