/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.ids.SID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class CfgAbsRoots
{
    public Map<SID, String> getAll() throws SQLException
    {
        return Cfg.getRoots();
    }

    public @Nullable String getNullable(SID sid) throws SQLException
    {
        return Cfg.getRootPathNullable(sid);
    }

    public @Nonnull String get(SID sid) throws SQLException
    {
        return checkNotNull(getNullable(sid));
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
