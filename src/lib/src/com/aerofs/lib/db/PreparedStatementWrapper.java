/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.db;

import java.sql.PreparedStatement;

public class PreparedStatementWrapper
{
    private PreparedStatement _ps;

    public PreparedStatement get()
    {
        return _ps;
    }

    public PreparedStatement set(PreparedStatement ps)
    {
        return _ps = ps;
    }

    public void close()
    {
        DBUtil.close(_ps);
        _ps = null;
    }
}
