package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.event.admin.EIInvalidateUserNameCache;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.google.inject.Inject;

public class HdInvalidateUserNameCache extends AbstractHdIMC<EIInvalidateUserNameCache>
{
    private final UserAndDeviceNames _uadn;

    @Inject
    public HdInvalidateUserNameCache(UserAndDeviceNames uadn)
    {
        _uadn = uadn;
    }

    @Override
    protected void handleThrows_(EIInvalidateUserNameCache ev) throws Exception
    {
        _uadn.clearUserNameCache_();
    }
}