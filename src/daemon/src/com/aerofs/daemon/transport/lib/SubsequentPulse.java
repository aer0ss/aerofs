/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.event.net.EOTpSubsequentPulse;
import com.aerofs.base.id.DID;
import org.slf4j.Logger;

import static com.aerofs.daemon.lib.DaemonParam.MAX_PULSE_FAILURES;
import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.doEOSubsequentPulsePrePulseChecks;
import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.schedule_;

/**
 * Event handler for {@link EOTpSubsequentPulse} events. This handler
 * only handles pulses rescheduled by the transport.
 */
public class SubsequentPulse implements IPulseHandlerImpl<EOTpSubsequentPulse>
{
    public SubsequentPulse(ITransportImpl tp)
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
        tp.pm().stopPulse(did, false);
    }

    @Override
    public boolean prepulsechecks_(EOTpSubsequentPulse ev)
    {
        return doEOSubsequentPulsePrePulseChecks(l, tp, tp.pm(), ev, MAX_PULSE_FAILURES);
    }

    @Override
    public boolean schednextpulse_(EOTpSubsequentPulse ev)
    {
        ev.addtry_();
        schedule_(l, tp.sched(), ev, ev.curtimeout_());
        return true;
    }

    private final ITransportImpl tp;

    private static final Logger l = Loggers.getLogger(SubsequentPulse.class);
}
