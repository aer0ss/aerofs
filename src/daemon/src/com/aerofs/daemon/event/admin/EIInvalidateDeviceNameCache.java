/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

public class EIInvalidateDeviceNameCache extends AbstractEBIMC
{
    public EIInvalidateDeviceNameCache()
    {
        super(Core.imce());
    }
}