package com.aerofs.lib.cfg;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;

public class CfgCACertFilename
{
    public String get()
    {
        return Util.join(AppRoot.abs(), Param.CA_CERT);
    }
}
