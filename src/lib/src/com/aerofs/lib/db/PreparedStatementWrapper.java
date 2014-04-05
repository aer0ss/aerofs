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
    private @Nullable PreparedStatement _ps;

    public PreparedStatement get()
    {
        return _ps;
    }

    public PreparedStatement get(Connection c, String stmt) throws SQLException
    {
        if (_ps == null) {
            _ps = c.prepareStatement(stmt);
        }
        return _ps;
    }

    public PreparedStatement set(@Nullable PreparedStatement ps)
    {
        return _ps = ps;
    }

    public void close()
    {
        DBUtil.close(_ps);
        _ps = null;
    }
}
