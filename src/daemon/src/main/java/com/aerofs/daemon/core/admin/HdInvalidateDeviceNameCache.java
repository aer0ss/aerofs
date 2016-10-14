/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.event.admin.EIInvalidateDeviceNameCache;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.core.UserAndDeviceNames;
import com.google.inject.Inject;

public class HdInvalidateDeviceNameCache extends AbstractHdIMC<EIInvalidateDeviceNameCache>
{
    private final UserAndDeviceNames _uadn;

    @Inject
    public HdInvalidateDeviceNameCache(UserAndDeviceNames uadn)
    {
        _uadn = uadn;
    }

    @Override
    protected void handleThrows_(EIInvalidateDeviceNameCache ev) throws Exception
    {
        _uadn.clearDeviceNameCache_();
    }
}