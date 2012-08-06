/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.DID;

/**
 * Event that is sent by the <code>Core</code> when it wants to check the liveness
 * of a peer. The transport should return an {@link EIPulseStopped} event when
 * pulsing stops <strong>for any reason</strong>.
 *
 * FIXME: Strictly doesn't have to extend AbstractEBIMC since the core doesn't need to wait for pulse results.
 * Done because CoreIMC requires AbstractEBIMC-derived events.
 */
public class EOStartPulse extends AbstractEBIMC
{
    /**
     * Constructor
     *
     * @param imc {@link IIMCExecutor} into which this event should be scheduled
     * @param did {@link DID} being checked for liveness
     */
    public EOStartPulse(IIMCExecutor imc, DID did)
    {
        super(imc);
        this.did = did;
    }

    public DID did()
    {
        return did;
    }

    @Override
    public String toString()
    {
        return "startpulse: d:" + did;
    }

    private final DID did;
}
