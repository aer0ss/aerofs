package com.aerofs.lib;

import com.aerofs.lib.cfg.Cfg;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;

public class Profiler
{
    private static final Logger l = Util.l(Profiler.class);

    private boolean _started;
    private long _ts;
    private long _threshold;
    private final boolean _adjustThreshold;
    private final String _label;

    /**
     * Creates a Profiler that will only output to the log if
     * the elapsed time between calls to start() and stop()
     * exceeds the specified threshold. If the threshold is 0,
     * the profiler will never output to the log.
     *
     * @param label The label to attach to this Profiler. Can be searched
     * for in the logs
     * @param threshold The threshold in milliseconds. If 0, profiler is
     * effectively disabled
     */
    public Profiler(@Nonnull String label, long threshold)
    {
        _adjustThreshold = false;
        _threshold = threshold;
        _label = label;
    }

    /**
     * Creates a Profiler with the default threshold specified in
     * the <code>profiler</code> file in approot.
     * <br/>
     * See {@link Profiler(String, long)} for details.
     *
     * @param label The label to attach to this Profiler. Can be searched
     * for in the logs
     */
    public Profiler(@Nonnull String label)
    {
        this(label, Cfg.profilerStartingThreshold());
    }

    /**
     * Creates a Profiler with the default threshold and no label.
     * <br/>
     * See {@link Profiler(String, long)} for details.
     */
    public Profiler()
    {
        this("");
    }

    /**
     * Starts the timer. Call this before executing the operation to be profiled.
     */
    public void start()
    {
        if (_threshold != 0) {
            assert !_started;
            _started = true;

            _ts = System.currentTimeMillis();
        }
    }

    /**
     * @return true if the timer was started, false if it was stopped, reset, or never
     * started
     */
    public boolean started()
    {
        return _started;
    }

    /**
     * Stops the timer and outputs a stack trace to the log detailing the location this
     * method was called and the time elapsed since the call to start(). Nothing will be
     * logged if the time elapsed is less than the threshold, or the threshold is zero.
     */
    public void stop()
    {
        if (_threshold != 0) {
            assert _started;
            _started = false;

            long diff = System.currentTimeMillis() - _ts;
            if (diff > _threshold) {
                l.debug(Util.stackTrace2string(new ProfilerTrace(diff).fillInStackTrace()));

                if (_adjustThreshold) {
                    _threshold = diff;
                }
            }
        }
    }

    /**
     * Resets the profiler, assuming it was previously started. It is valid to call
     * start() after a call to reset().
     */
    public void reset()
    {
        if (_threshold != 0) {
            assert _started;
            _started = false;
            l.debug(formatMessage("reset"));
        }
    }

    private String formatMessage(String message)
    {
        if (_label.isEmpty()) {
            return message;
        } else {
            return _label + ": " + message;
        }
    }

    /**
     * Custom Exception class to make it easier to see that the stacktrace
     * is from a Profiler and not an actual exception being thrown.
     */
    private class ProfilerTrace extends Exception
    {
        private static final long serialVersionUID = 1L;

        public ProfilerTrace(long elapsedTime)
        {
            super(formatMessage(elapsedTime + " ms"));
        }
    }
}
