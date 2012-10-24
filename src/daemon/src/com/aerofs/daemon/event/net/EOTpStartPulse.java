/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.event.net;

import com.aerofs.lib.id.DID;

import static com.aerofs.daemon.transport.lib.PulseManager.PulseToken;

/**
 * Event that is re-enqueued by the transport when it receives an {@link EOStartPulse}
 * event from the core. This transport-only version contains implementation details
 * that the core doesn't need to know about.
 */
public class EOTpStartPulse implements IPulseEvent
{
    /**
     * Constructor
     *
     * @param did {@link DID} being checked for liveness
     */
    public EOTpStartPulse(DID did)
    {
        this.did = did;
        _tok = null;
    }

    @Override
    public DID did()
    {
        return did;
    }

    @Override
    public PulseToken tok_()
    {
        return _tok;
    }

    @Override
    public void tok_(PulseToken tok)
    {
        assert tok != null : ("cannot use null tok");
        this._tok = tok;
    }

    @Override
    public String toString()
    {
        return "tpstartpulse: d:" + did + " tok:" + (_tok == null ? "null" : _tok);
    }

    private final DID did;
    private PulseToken _tok;
}
