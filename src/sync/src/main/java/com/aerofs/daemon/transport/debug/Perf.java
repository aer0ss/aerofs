/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.debug;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

/**
 * Simple class to measure the time psent doing work vs not doing work
 */
public final class Perf
{
    private static final Logger l = Loggers.getLogger(Perf.class);
    private static final int SAMPLE_SIZE = 1000;

    private final String _name;
    private int _count;
    private long _start;
    private long _last;
    private long _totalTimeLocal;
    private long _totalTimeGlobal;

    public Perf(String name)
    {
        _name = name;
    }

    /**
     * Note: this class is not thread-safe
     */
    public void start()
    {
        _start = System.nanoTime();
    }

    public void stop()
    {
        long stop = System.nanoTime();

        long timeLocal = stop - _start;
        long timeGlobal = _start - _last;
        _last = _start;

        _count++;
        _totalTimeLocal += timeLocal;
        _totalTimeGlobal += timeGlobal;

        if (_count == SAMPLE_SIZE) {
            float percent = 100f * _totalTimeLocal / _totalTimeGlobal;
            l.info("#### time spent for {}: {}%", _name, percent);
            _totalTimeLocal = _totalTimeGlobal = _count = 0;
        }
    }
}
