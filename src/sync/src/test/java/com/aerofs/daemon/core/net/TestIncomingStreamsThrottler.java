/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.ids.DID;
import com.aerofs.daemon.core.net.throttling.LimitMonitor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestIncomingStreamsThrottler extends AbstractTest
{
    IncomingStreamsThrottler throttler;
    DID did = new DID(DID.ZERO);
    DID didTwo = new DID(DID.generate());
    StreamID streamOne = new StreamID(1);
    StreamID streamTwo = new StreamID(2);

    @Mock CfgDatabase db;
    @Mock UnicastInputOutputStack stack;
    @Mock Metrics metrics;
    @Mock LimitMonitor limitMonitor;

    @Before
    public void setup() throws Exception
    {
        when(db.getInt(Key.LOW_CHUNK_WATERMARK)).thenReturn(2);
        when(db.getInt(Key.HIGH_CHUNK_WATERMARK)).thenReturn(5);
        when(metrics.getMaxUnicastSize_()).thenReturn(1);
        throttler = new IncomingStreamsThrottler(db, metrics, new IncomingStreams(stack));
        throttler.setLimitMonitor_(limitMonitor);
    }

    @Test
    public void shouldNotPauseDeviceWhenChunkCountIsBelowHighWatermark() throws Exception
    {
        for (int i = 0; i < 4; i++) {
            throttler.onChunkReceived_(did, streamOne);
        }

        verify(limitMonitor, never()).pauseDevice_(did);

        throttler.onChunkProcessed_(did, streamOne);
        throttler.onChunkReceived_(did, streamOne);

        verify(limitMonitor, never()).pauseDevice_(did);
    }

    @Test
    public void shouldNotPauseDeviceWhenChunkCountFromMultipleStreamsIsBelowHighWatermark()
            throws Exception
    {
        throttler.onChunkReceived_(did, streamOne);
        throttler.onChunkReceived_(did, streamTwo);
        throttler.onChunkReceived_(did, streamOne);
        throttler.onChunkReceived_(did, streamTwo);

        verify(limitMonitor, never()).pauseDevice_(did);

        throttler.onChunkProcessed_(did, streamOne);
        throttler.onChunkReceived_(did, streamTwo);

        verify(limitMonitor, never()).pauseDevice_(did);
    }

    @Test
    public void shouldPauseWhenDeviceTotalChunkCountIsGreaterOrEqualToHighWatermark()
            throws Exception
    {
        for (int i = 0; i < 2; i++) {
            throttler.onChunkReceived_(did, streamOne);
            throttler.onChunkReceived_(did, streamTwo);
        }
        throttler.onChunkReceived_(did, streamOne);

        verify(limitMonitor).pauseDevice_(did);
    }

    @Test
    public void shouldResumePausedDeviceWhenDeviceTotalChunkCountIsLessThanOrEqualToLowWatermark()
            throws Exception
    {
        for (int i = 0; i < 3; i++) {
            throttler.onChunkReceived_(did, streamOne);
            throttler.onChunkReceived_(did, streamTwo);
        }

        verify(limitMonitor).pauseDevice_(did);
        verify(limitMonitor, never()).resumeDevice_(did);

        for (int i = 0; i < 2; i++) {
            throttler.onChunkProcessed_(did, streamOne);
            throttler.onChunkProcessed_(did, streamTwo);
        }

        verify(limitMonitor).resumeDevice_(did);
    }

    @Test
    public void shouldNotAggregateChunkCountsAcrossDevices() throws Exception
    {
        for (int i = 0; i < 2; i++) {
            throttler.onChunkReceived_(did, streamOne);
            throttler.onChunkReceived_(didTwo, streamOne);
        }
        throttler.onChunkReceived_(did, streamOne);

        verify(limitMonitor, never()).pauseDevice_(any(DID.class));

        throttler.onChunkReceived_(did, streamOne);
        throttler.onChunkReceived_(did, streamTwo);

        verify(limitMonitor).pauseDevice_(did);
        verify(limitMonitor, never()).pauseDevice_(didTwo);
    }

    @Test
    public void shouldDecrementByAllStreamChunksWhenStreamInvalidated() throws Exception
    {
        for (int i = 0; i < 5; i++) {
            throttler.onChunkReceived_(did, streamOne);
        }

        verify(limitMonitor).pauseDevice_(did);

        throttler.onStreamInvalidated_(did, streamOne);

        verify(limitMonitor).resumeDevice_(did);
    }
}
