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

    public Map<SID, String> get_() throws SQLException
    {
        return _db.getRoots_();
    }

    public void add_(SID sid, String absPath) throws SQLException
    {
        _db.addRoot_(sid, absPath);
    }

    public void remove_(SID sid) throws SQLException
    {
        _db.removeRoot_(sid);
    }

    public void move_(SID sid, String newAbsPath) throws SQLException
    {
        _db.moveRoot_(sid, newAbsPath);
    }
}
