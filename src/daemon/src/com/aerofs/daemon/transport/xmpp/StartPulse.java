/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.transport.lib.ITransportImpl;
import com.aerofs.daemon.transport.lib.StartPulseBase;

/**
 * XMPP-specific event handler for {@link com.aerofs.daemon.event.net.EOTpStartPulse} events. This handler
 * only handles pulses scheduled by the core
 */
public final class StartPulse<T extends ITransportImpl> extends StartPulseBase<T>
{
    private final PresenceStore _presenceStore;

    public StartPulse(T transport, PresenceStore presenceStore)
    {
        super(transport);
        this._presenceStore = presenceStore;
    }

    @Override
    public boolean prepulsechecks_(EOTpStartPulse ev)
    {
        DID did = ev.did();

        assertnopulse(did);

        if (!_presenceStore.has(did)) { // check if we're already offline
            l.info("d:" + did + " offline during pulse; term pulse");
            notifypulsestopped_(did);
            return false;
        }

        return true;
    }
}
