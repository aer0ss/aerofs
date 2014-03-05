/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.EOTpSubsequentPulse;
import com.aerofs.lib.sched.IScheduler;
import org.slf4j.Logger;

import static com.aerofs.daemon.lib.DaemonParam.Pulse.MAX_PULSE_FAILURES;
import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.doEOSubsequentPulsePrePulseChecks;
import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.schedule_;

/**
 * Event handler for {@link EOTpSubsequentPulse} events. This handler
 * only handles pulses rescheduled by the transport.
 */
public class SubsequentPulse implements IPulseHandler<EOTpSubsequentPulse>
{
    private static final Logger l = Loggers.getLogger(SubsequentPulse.class);

    private final IScheduler scheduler;
    private final PulseManager pulseManager;
    private final IUnicastInternal unicast;

    public SubsequentPulse(IScheduler scheduler, PulseManager pulseManager, IUnicastInternal unicast)
    {
        this.scheduler = scheduler;
        this.pulseManager = pulseManager;
        this.unicast = unicast;
    }

    @Override
    public void notifypulsestopped_(DID did)
    {
        pulseManager.stopPulse(did, false);
    }

    @Override
    public boolean prepulsechecks_(EOTpSubsequentPulse ev)
    {
        return doEOSubsequentPulsePrePulseChecks(l, unicast, pulseManager, ev, MAX_PULSE_FAILURES);
    }

    @Override
    public boolean schednextpulse_(EOTpSubsequentPulse ev)
    {
        ev.addtry_();
        schedule_(l, scheduler, ev, ev.curtimeout_());
        return true;
    }
}
