package com.aerofs.ui.update;

import com.aerofs.lib.cfg.Cfg;

import static com.aerofs.lib.cfg.CfgDatabase.LAST_VER;

public class PostUpdate
{
    public static boolean updated()
    {
        return !Cfg.ver().equals(Cfg.db().get(LAST_VER));
    }
}
