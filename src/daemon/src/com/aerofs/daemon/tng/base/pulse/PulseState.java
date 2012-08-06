/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse;

import com.aerofs.lib.async.UncancellableFuture;

/**
 * Maintains the state of Pulsing for a remote peer on a given transport.
 * <p/>
 * Pulse messages use the pulse ID stored in this PulseState object and expect pulse replies with
 * the same pulse ID. If an ongoing pulse request succeeds or times-out, the pulse ID is bumped so
 * the next pulse request uses a new pulse ID. This prevents stale responses and timeouts from
 * previous pulses affecting the current pulse request.
 */
public class PulseState
{
    private final int _numberOfTriesBeforeDisconnect;
    private final long _startingRetryDelay;
    private final long _maxRetryDelay;

    private int _pulseId = 1;
    private PulseState.Ongoing _ongoing = null;

    public PulseState(int numberOfTriesBeforeDisconnect, long startingRetryDelay,
            long maxRetryDelay)
    {
        _numberOfTriesBeforeDisconnect = numberOfTriesBeforeDisconnect;
        _startingRetryDelay = startingRetryDelay;
        _maxRetryDelay = maxRetryDelay;
    }

    /**
     * Returns whether an ongoing pulse exists
     *
     * @return true if a pulse is ongoing, false otherwise
     */
    public boolean isOngoing()
    {
        return _ongoing != null;
    }

    /**
     * Returns the current pulse ID that was or will be used for subsequent pulsing messages. This
     * ID is only incremented when a pulse times-out or succeeds.
     *
     * @return the current pulse ID
     */
    public int getCurrentPulseId_()
    {
        return _pulseId;
    }

    /**
     * Returns whether the connection upon which the pulse is occurring should be disconnected
     *
     * @return true if the connection should be disconnected, false otherwise
     */
    public boolean shouldDisconnect_()
    {
        if (isOngoing()) {
            return _ongoing.shouldDisconnect_();
        }
        return false;
    }

    /**
     * Returns the time in milliseconds to wait before timing-out an ongoing pulse
     *
     * @return timeout in milliseconds
     */
    public long getTimeoutDelay_()
    {
        if (isOngoing()) {
            return _ongoing.getRetryDelay_();
        }
        return _startingRetryDelay;
    }

    /**
     * Tells the PulseState object that a pulse was started
     *
     * @param pulseFuture The future that is set when pulsing succeeds/fails
     */
    public void pulseStarted_(UncancellableFuture<Void> pulseFuture)
    {
        assert !isOngoing();

        _ongoing = new PulseState.Ongoing(pulseFuture);
    }

    /**
     * Tells the PulseState object that an ongoing pulse has timed out. This increases the pulse ID
     * in preparation for subsequent pulses
     */
    public void pulseTimedOut_()
    {
        assert isOngoing();

        _ongoing._numberOfTries++;
        _pulseId++;
    }

    /**
     * Tells the PulseState object that an ongoing pulse was replied to successfully. This increases
     * the pulse ID in preparation for subsequent pulses
     */
    public void pulseCompleted_()
    {
        assert isOngoing();

        _ongoing._completionFuture.set(null);
        _ongoing = null;
        _pulseId++;
    }

    /**
     * Destroys the ongoing pulse, setting the pulse future with the specified exception
     *
     * @param exception The error that caused the pulse to fail
     */
    public void destroy_(Exception exception)
    {
        if (_ongoing != null) {
            _ongoing._completionFuture.setException(exception);
            _ongoing = null;
            _pulseId = 1;
        }
    }

    /**
     * Represents an ongoing pulse to a remote peer. Contains the future that will contain the
     * pulse's result and the number of times the pulse was attempted.
     */
    private final class Ongoing
    {
        private final UncancellableFuture<Void> _completionFuture;
        private int _numberOfTries = 0;

        public Ongoing(UncancellableFuture<Void> future)
        {
            _completionFuture = future;
        }

        public boolean shouldDisconnect_()
        {
            return _numberOfTries % _numberOfTriesBeforeDisconnect == 0;
        }

        public long getRetryDelay_()
        {
            long coefficient = (long) Math.pow(2, _numberOfTries);
            return Math.min(coefficient * _startingRetryDelay, _maxRetryDelay);
        }
    }
}
