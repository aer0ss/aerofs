/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.id.SID;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Map;

/**
 *
 */
public class CfgAbsRoots
{
    private final CfgDatabase _db;

    @Inject
    public CfgAbsRoots(CfgDatabase db)
    {
        _db = db;
    }

    public Map<SID, String> get() throws SQLException
    {
        return _db.getRoots();
    }

    public void add(SID sid, String absPath) throws SQLException
    {
        _db.addRoot(sid, absPath);
    }

    public void remove(SID sid) throws SQLException
    {
        _db.removeRoot(sid);
    }

    public void move(SID sid, String newAbsPath) throws SQLException
    {
        _db.moveRoot(sid, newAbsPath);
    }
}
