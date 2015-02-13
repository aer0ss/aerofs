package com.aerofs.lib.cfg;

import com.aerofs.ids.DID;

/**
 * The local peer's device id.
 *
 * Note that it's difficult to let CfgLocalDID extend DID because we might
 * not know the value of self DID at the time the class is instantiated
 * (which is done by the injector),
 */
public class CfgLocalDID
{
    public DID get()
    {
        return Cfg.did();
    }
}
