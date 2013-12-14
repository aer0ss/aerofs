/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.lib.event.Prio;

import javax.annotation.Nullable;

public final class UnicastProxy implements IUnicast
{
    private IUnicast unicast;

    public void setRealUnicast(IUnicast unicast)
    {
        this.unicast = unicast;
    }

    @Override
    public Object send(DID did, @Nullable IResultWaiter wtr, Prio pri, byte[][] bss, @Nullable Object cke)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        return unicast.send(did, wtr, pri, bss, cke);
    }
}
