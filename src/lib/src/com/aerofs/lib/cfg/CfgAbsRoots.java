/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.id.SID;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Map;

public class CfgAbsRoots
{
    public Map<SID, String> get()
    {
        return Cfg.getRoots();
    }

    public @Nullable String get(SID sid)
    {
        return Cfg.getRootPath(sid);
    }

    public void add(SID sid, String absPath) throws SQLException
    {
        Cfg.addRoot(sid, absPath);
    }

    public void remove(SID sid) throws SQLException
    {
        Cfg.removeRoot(sid);
    }

    public void move(SID sid, String newAbsPath) throws SQLException
    {
        Cfg.moveRoot(sid, newAbsPath);
    }
}
