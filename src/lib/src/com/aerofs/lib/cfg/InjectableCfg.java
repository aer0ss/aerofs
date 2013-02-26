/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;

/**
 * Wrapper class around Cfg's static methods to allow for easy injection
 * Feel free to add methods to this class as needed
 */
public class InjectableCfg
{
    public boolean inited()      { return Cfg.inited(); }
    public String ver()          { return Cfg.ver(); }
    public DID did()             { return Cfg.did(); }
    public UserID user()         { return Cfg.user(); }
}
