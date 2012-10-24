package com.aerofs.lib.cfg;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.C;
import com.aerofs.lib.Util;

public class CfgCACertFilename
{
    public String get()
    {
        return Util.join(AppRoot.abs(), C.CA_CERT);
    }
}
