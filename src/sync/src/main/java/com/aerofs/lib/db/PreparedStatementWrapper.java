/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.db;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PreparedStatementWrapper
{
    private final String _stmt;
    private @Nullable PreparedStatement _ps;

    public PreparedStatementWrapper(String stmt)
    {
        _stmt = stmt;
    }

    public PreparedStatement get(Connection c) throws SQLException
    {
        if (_ps == null) _ps = c.prepareStatement(_stmt);
        return _ps;
    }

    public void close()
    {
        DBUtil.close(_ps);
        _ps = null;
    }
}
