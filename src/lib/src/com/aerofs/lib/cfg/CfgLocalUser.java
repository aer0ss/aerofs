package com.aerofs.lib.cfg;

import com.aerofs.base.id.UserID;

/**
 * The local peer's user id
 */
public class CfgLocalUser
{
    public UserID get()
    {
        return Cfg.user();
    }
}
