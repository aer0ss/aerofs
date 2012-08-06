/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse;

import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

import static com.aerofs.testlib.FutureAssert.assertNoThrow;
import static com.aerofs.testlib.FutureAssert.assertThrows;
import static org.junit.Assert.*;

public class TestPulseState extends AbstractTest
{
    public final int NUMBER_OF_TRIES_BEFORE_DISCONNECT = 2;
    public final long START_RETRY_DELAY = 1000;
    public final long MAX_RETRY_DELAY = 4000;

    PulseState pulseState;

    @Before
    public void setUp() throws Exception
    {
        pulseState = new PulseState(NUMBER_OF_TRIES_BEFORE_DISCONNECT,
                START_RETRY_DELAY, MAX_RETRY_DELAY);
    }

    @Test
    public void shouldSetOngoingPulseStateWhenNoPreviousOngoingPulseExistsAndSuccessfullyCompeltePulsing() throws Exception
    {
        assertFalse(pulseState.isOngoing());

        UncancellableFuture<Void> pulseFuture = UncancellableFuture.create();
        pulseState.pulseStarted_(pulseFuture);

        assertTrue(pulseState.isOngoing());
        assertFalse(pulseFuture.isDone());

        pulseState.pulseCompleted_();

        assertNoThrow(pulseFuture);
    }

    @Test
    public void shouldStartPulseThenTimeoutThenCompleteSuccessfullyWithCorrectPulseId() throws Exception
    {
        UncancellableFuture<Void> pulseFuture = UncancellableFuture.create();
        pulseState.pulseStarted_(pulseFuture);

        int firstPulseId = pulseState.getCurrentPulseId_();

        assertEquals(START_RETRY_DELAY, pulseState.getTimeoutDelay_());
        pulseState.pulseTimedOut_();

        // Ensure we're using a new pulseId that is larger than the last
        assertTrue(pulseState.getCurrentPulseId_() > firstPulseId);
        assertFalse(pulseFuture.isDone());

        pulseState.pulseCompleted_();

        assertNoThrow(pulseFuture);
    }

    @Test
    public void shouldPulseThenCompleteSuccessfullyThenPulseWithDifferentPulseId() throws Exception
    {
        UncancellableFuture<Void> pulseFuture = UncancellableFuture.create();
        pulseState.pulseStarted_(pulseFuture);

        assertTrue(pulseState.isOngoing());
        assertFalse(pulseFuture.isDone());

        int firstPulseId = pulseState.getCurrentPulseId_();

        pulseState.pulseCompleted_();

        assertNoThrow(pulseFuture);

        pulseFuture = UncancellableFuture.create();
        pulseState.pulseStarted_(pulseFuture);

        assertTrue(pulseState.getCurrentPulseId_() > firstPulseId);

        assertTrue(pulseState.isOngoing());
        assertFalse(pulseFuture.isDone());

        pulseState.pulseCompleted_();

        assertNoThrow(pulseFuture);
    }

    @Test
    public void shouldThrowExceptionWhenPulsingAttemptedWhilePulseIsOngoing() throws Exception
    {
        UncancellableFuture<Void> pulseFuture = UncancellableFuture.create();
        pulseState.pulseStarted_(pulseFuture);

        assertTrue(pulseState.isOngoing());

        try {
            pulseState.pulseStarted_(UncancellableFuture.<Void>create());
        } catch (AssertionError e) {
            return;
        }
        fail();
    }

    @Test
    public void shouldDoubleRetryDelayUponEachTimeout() throws Exception
    {
        UncancellableFuture<Void> pulseFuture = UncancellableFuture.create();
        pulseState.pulseStarted_(pulseFuture);
        assertEquals(START_RETRY_DELAY, pulseState.getTimeoutDelay_());

        pulseState.pulseTimedOut_();
        assertEquals(START_RETRY_DELAY * 2, pulseState.getTimeoutDelay_());

        pulseState.pulseTimedOut_();
        assertEquals(START_RETRY_DELAY * 4, pulseState.getTimeoutDelay_());

        pulseState.pulseTimedOut_();
        assertEquals(MAX_RETRY_DELAY, pulseState.getTimeoutDelay_());
    }

    @Test
    public void shouldDisconnectWhenMaxTriesReached() throws Exception
    {
        UncancellableFuture<Void> pulseFuture = UncancellableFuture.create();
        pulseState.pulseStarted_(pulseFuture);

        for (int i = 0; i < NUMBER_OF_TRIES_BEFORE_DISCONNECT; i++) {
            pulseState.pulseTimedOut_();
        }

        assertTrue(pulseState.shouldDisconnect_());

        pulseState.pulseTimedOut_();

        assertFalse(pulseState.shouldDisconnect_());

        for (int i = 0; i < NUMBER_OF_TRIES_BEFORE_DISCONNECT - 1; i++) {
            pulseState.pulseTimedOut_();
        }

        assertTrue(pulseState.shouldDisconnect_());
    }

    @Test
    public void shouldSetFutureWithExceptionWhenDestroyCalled() throws Exception
    {
        UncancellableFuture<Void> pulseFuture = UncancellableFuture.create();
        pulseState.pulseStarted_(pulseFuture);

        pulseState.destroy_(new Exception("Test"));

        assertThrows(pulseFuture, Exception.class, "Test");
    }

}
