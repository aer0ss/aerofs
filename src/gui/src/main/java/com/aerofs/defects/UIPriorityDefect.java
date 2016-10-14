/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.Loggers;
import com.aerofs.labeling.L;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.ritual.IRitualClientProvider;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.google.common.collect.Queues;
import org.slf4j.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class UIPriorityDefect extends PriorityDefect
{
    private static final Logger l = Loggers.getLogger(UIPriorityDefect.class);

    private final IRitualClientProvider _ritualProvider;

    private UIPriorityDefect(InjectableSPBlockingClientFactory spFactory,
            Executor executor, IRitualClientProvider ritualProvider)
    {
        super(spFactory, executor);

        _ritualProvider = ritualProvider;
    }

    @Override
    public void sendSyncIgnoreErrors()
    {
        String progress = _sampleCPU
                ? "Sampling " + L.product() + " CPU usage"
                : "Submitting";

        UI.get().addProgress(progress, true);

        try {
            sendSync();
            UI.get().notify(MessageType.INFO, "Problem submitted. Thank you!");
        } catch (Exception e) {
            l.warn("Failed to send priority defect:", e);
            UI.get()
                    .notify(MessageType.ERROR,
                            "Failed to report the problem. " + "Please try again later.");
        } finally {
            UI.get().removeProgress(progress);
        }
    }

    @Override
    protected void logThreadsImpl() throws Exception
    {
        _ritualProvider.getBlockingClient().logThreads();
    }

    @Override
    protected PBDumpStat getDaemonStatusImpl(PBDumpStat template) throws Exception
    {
        return _ritualProvider.getBlockingClient().dumpStats(template).getStats();
    }

    public static class Factory
    {
        private final Executor _executor = new ThreadPoolExecutor(
                0, 1,                                           // at most 1 thread
                30, TimeUnit.SECONDS,                           // idle threads TTL
                Queues.<Runnable>newLinkedBlockingQueue(10),    // bounded event queue
                // abort on overflow. This is ok because priority defects are initiated by the user.
                new AbortPolicy()
        );

        private final IRitualClientProvider _ritualProvider;

        public Factory(IRitualClientProvider ritualProvider)
        {
            _ritualProvider = ritualProvider;
        }

        public PriorityDefect newPriorityDefect()
        {
            return new UIPriorityDefect(newMutualAuthClientFactory(), _executor, _ritualProvider);
        }
    }
}
