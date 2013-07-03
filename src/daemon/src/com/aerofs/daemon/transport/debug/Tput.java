/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.debug;


import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import org.slf4j.Logger;

/**
 * Simple class to measure the throughput of a system
 */
public class Tput
{
    private static final Logger l = Loggers.getLogger(Tput.class);
    private static final int SAMPLE_SIZE = 1000;

    final String _name;
    long _start;
    long _totalBytes;
    int _count;

    /**
     * @param name: string that will be displayed in the logs
     */
    public Tput(String name)
    {
        _name = name;
    }

    /**
     * This method is not thread safe.
     */
    public void observe(long bytes)
    {
        if (_start == 0) {
            _start = System.nanoTime();
            return;
        }

        _totalBytes += bytes;
        _count++;

        if (_count >= SAMPLE_SIZE) {
            long now = System.nanoTime();

            long totalTime = (now - _start) / 1000000; // milliseconds
            l.info("##### TPUT {}: {} MB/s", _name,  ((float)_totalBytes / totalTime) / ((float)C.MB / C.SEC));

            _totalBytes = 0;
            _start = now;
            _count = 0;
        }

    }
}
