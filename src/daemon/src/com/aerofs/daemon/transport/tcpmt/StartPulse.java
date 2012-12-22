/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.transport.lib.StartPulseBase;
import com.aerofs.base.id.DID;

/**
 * TCPMT-specific event handler for {@link com.aerofs.daemon.event.net.EOTpStartPulse} events. This handler
 * only handles pulses scheduled by the core
 */
public class StartPulse extends StartPulseBase<TCP>
{
    public StartPulse(TCP t)
    {
        super(t);
    }

    @Override
    public boolean prepulsechecks_(EOTpStartPulse ev)
    {
        DID did = ev.did();

        assertnopulse(did);

        // stop pulses if the device is offline from the transport's POV

        if (!tp.arp().exists(did)) {
            notifypulsestopped_(did);
            l.info("d:" + did + " offline - term hd");
            return false;
        }

        // special case for SP

        if (tp.hm().has(did)) {
            notifypulsestopped_(did);
            l.info("d:" + did + " muod - term hd");
            return false;
        }

        return true;
    }
}
