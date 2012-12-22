/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.id.SID;

class CfgRootSID
{
    public SID get()
    {
        return Cfg.rootSID();
    }
}
