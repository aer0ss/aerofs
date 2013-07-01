/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.transport.lib.StartPulseBase;
import com.aerofs.base.id.DID;

/**
 * TCPMT-specific event handler for {@link com.aerofs.daemon.event.net.EOTpStartPulse} events. This handler
 * only handles pulses scheduled by the core
 */
class StartPulse extends StartPulseBase<TCP>
{
    private final ARP _arp;

    public StartPulse(TCP t, ARP arp)
    {
        super(t);
        _arp = arp;
    }

    @Override
    public boolean prepulsechecks_(EOTpStartPulse ev)
    {
        DID did = ev.did();

        assertnopulse(did);

        // stop pulses if the device is offline from the transport's POV

        if (!_arp.exists(did)) {
            notifypulsestopped_(did);
            l.info("d:" + did + " offline - term hd");
            return false;
        }

        return true;
    }
}
