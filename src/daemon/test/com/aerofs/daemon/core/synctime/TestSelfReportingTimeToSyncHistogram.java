/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.synctime.api.ClientSideHistogram;
import com.aerofs.synctime.client.TimeToSyncClient;
import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import static com.aerofs.daemon.core.synctime.Params.SEND_HISTOGRAM_INTERVAL;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.notNull;

public class TestSelfReportingTimeToSyncHistogram extends AbstractTest
{
    @Mock CoreScheduler sched;
    @Mock TimeToSyncClient client;
    @InjectMocks SelfReportingTimeToSyncHistogram histogram;

    @Test
    public void whenUpdateIsCalledForTheFirstTime_ShouldScheduleReport()
    {
        updateHistogram();
        verify(sched).schedule(histogram, SEND_HISTOGRAM_INTERVAL);
    }

    @Test
    public void whenUpdateIsCalledMultipleTimes_ShouldScheduleReportOnlyOnce()
    {
        for (int i = 0; i < 3; i++) updateHistogram();
        verify(sched, times(1)).schedule(histogram, SEND_HISTOGRAM_INTERVAL);
    }

    @Test
    public void whenHandlingScheduledReport_ShouldReschedule()
    {
        histogram.handle_();
        verify(sched).schedule(histogram, SEND_HISTOGRAM_INTERVAL);
    }

    @Test
    public void whenHandlingScheduledReportWithNoUpdates_ShouldSendToServer()
    {
        histogram.handle_();
        verifyZeroInteractions(client);
    }

    @Test
    public void whenHandlingScheduledReportAfterUpdates_ShouldSendHistogramsForDevices()
    {
        Set<DID> dids = ImmutableSet.of(DID.generate(), DID.generate(), DID.generate());
        for (DID did : dids) updateHistogram(did);

        histogram.handle_();

        for (DID did : dids) {
            verify(client).sendHistogramForDevice(eq(did.toStringFormal()),
                    notNull(ClientSideHistogram.class));
        }
    }

    private void updateHistogram()
    {
        updateHistogram(DID.generate());
    }

    private void updateHistogram(DID did)
    {
        histogram.update_(did, OID.generate(), mock(TimeToSync.class));
    }
}
