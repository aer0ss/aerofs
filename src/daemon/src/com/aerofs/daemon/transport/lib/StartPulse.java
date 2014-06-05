/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.lib.sched.IScheduler;
import org.slf4j.Logger;

import static com.aerofs.daemon.transport.lib.PulseHandlerUtil.doEOStartPulseSchedule;
import static com.google.common.base.Preconditions.checkState;

/**
 * Handles all {@link EOTpStartPulse} events
 */
public class StartPulse implements IPulseHandler<EOTpStartPulse>
{
    private final Logger l = Loggers.getLogger(StartPulse.class);

    private final IScheduler scheduler;
    private final PulseManager pulseManager;
    private final IDevicePresenceService presenceManager;

    public StartPulse(IScheduler scheduler, PulseManager pulseManager, IDevicePresenceService presenceManager)
    {
        this.scheduler = scheduler;
        this.pulseManager = pulseManager;
        this.presenceManager = presenceManager;
    }

    @Override
    public boolean prepulsechecks_(EOTpStartPulse ev)
    {
        DID did = ev.did();

        assertnopulse(did);

        // stop pulses if the device is offline from the transport's POV

        if (!presenceManager.isPotentiallyAvailable(did)) {
            l.info("{} offline during pulse; term pulse", did);
            notifypulsestopped_(did);
            return false;
        }

        return true;
    }

    @Override
    public void notifypulsestopped_(DID did)
    {
        pulseManager.stopPulse(did, true); // force a core notification
    }

    @Override
    public boolean schednextpulse_(EOTpStartPulse ev)
    {
        doEOStartPulseSchedule(l, scheduler, ev.did(), ev.tok_());
        return true;
    }

    /**
     * Prevent multiple sequential pulses from the core from being scheduled
     *
     * @param did {@link DID} of the remote peer for whom there should be no in-progress pulses
     */
    protected void assertnopulse(DID did)
    {
        checkState(pulseManager.getInProgressPulse(did) == null, "d:" + did + " core already sched pulse");
    }
}
