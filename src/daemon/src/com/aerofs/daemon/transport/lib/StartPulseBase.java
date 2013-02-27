/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import org.slf4j.Logger;

import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.doEOStartPulseSchedule;

/**
 * Base class for all transport-specific {@link EOTpStartPulse} event handlers
 */
public abstract class StartPulseBase<T extends ITransportImpl> implements IPulseHandlerImpl<EOTpStartPulse>
{
    public StartPulseBase(T tp)
    {
        this.tp = tp;
    }

    @Override
    public ITransportImpl tp()
    {
        return tp;
    }

    @Override
    public void notifypulsestopped_(DID did)
    {
        tp.pm().stopPulse(did, true); // force a core notification
    }

    @Override
    public boolean schednextpulse_(EOTpStartPulse ev)
    {
        doEOStartPulseSchedule(l, tp.sched(), ev.did(), ev.tok_());
        return true;
    }

    /**
     * Prevent multiple sequential pulses from the core from being scheduled
     *
     * @param did {@link DID} of the remote peer for whom there should be no in-progress pulses
     */
    protected void assertnopulse(DID did)
    {
        assert (tp.pm().getInProgressPulse(did) == null) : ("d:" + did + " core already sched pulse");
    }

    //
    // Members
    //

    protected final T tp;
    protected final Logger l = Loggers.getLogger(this.getClass());
}
