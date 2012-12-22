/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.transport.lib.StartPulseBase;

/**
 * XMPP-specific event handler for {@link com.aerofs.daemon.event.net.EOTpStartPulse} events. This handler
 * only handles pulses scheduled by the core
 */
public class StartPulse extends StartPulseBase<XMPP>
{
    public StartPulse(XMPP x)
    {
        super(x);
    }

    @Override
    public boolean prepulsechecks_(EOTpStartPulse ev)
    {
        DID did = ev.did();

        assertnopulse(did);

        // check if we're already offline

        if (!tp.xpm().has(did)) {
            l.info("d:" + did + " offline during pulse; term pulse");
            notifypulsestopped_(did);
            return false;
        }

        return true;
    }
}
