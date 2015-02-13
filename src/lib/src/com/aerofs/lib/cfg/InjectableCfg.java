/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.CfgDatabase.Key;

import java.sql.SQLException;
import java.util.Map;

/**
 * Wrapper class around Cfg's static methods to allow for easy injection
 * Feel free to add methods to this class as needed
 */
public class InjectableCfg
{
    public boolean inited()             { return Cfg.inited(); }
    public String ver()                 { return Cfg.ver(); }
    public DID did()                    { return Cfg.did(); }
    public UserID user()                { return Cfg.user(); }
    public String absRTRoot()           { return Cfg.absRTRoot(); }
    public CfgDatabase db()             { return Cfg.db(); }
    public StorageType storageType()    { return Cfg.storageType(); }
    public Map<SID, String> getRoots()
            throws SQLException         { return Cfg.getRoots(); }
    public Map<Key, String> dumpDB()    { return Cfg.dumpDb(); }
}
