/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class UnicastProxy implements IUnicast
{
    private IUnicast unicast;

    public void setRealUnicast(IUnicast unicast)
    {
        this.unicast = unicast;
    }

    @Override
    public Object send(DID did, byte[][] bss, @Nullable IResultWaiter wtr) throws ExTransportUnavailable, ExDeviceUnavailable {
        return unicast.send(did, bss, wtr);
    }

    @Override
    public void send(@Nonnull Object cookie, byte[][] bss, @Nullable IResultWaiter wtr) {
        unicast.send(cookie, bss, wtr);
    }
}
