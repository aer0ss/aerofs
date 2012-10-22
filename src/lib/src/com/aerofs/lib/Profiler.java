package com.aerofs.lib;

import com.aerofs.lib.cfg.Cfg;

import javax.annotation.Nonnull;

public class Profiler {

    private boolean _started;
    private long _ts;
    private long _threshold;
    private final boolean _adjustThreshold;
    private final String _label;

    public Profiler(@Nonnull String label)
    {
        _adjustThreshold = false;
        _threshold = Cfg.profilerStartingThreshold();

        String stackTraceMessage = "PROFILER";
        if (!label.isEmpty()) {
            stackTraceMessage = stackTraceMessage + " - " + label;
        }
        _label = stackTraceMessage;
    }

    public Profiler()
    {
        this("");
    }

    public void start()
    {
        if (_threshold != 0) {
            assert !_started;
            _started = true;

            _ts = System.currentTimeMillis();
        }
    }

    public boolean started()
    {
        return _started;
    }

    public void stop()
    {
        if (_threshold != 0) {
            assert _started;
            _started = false;

            long diff = System.currentTimeMillis() - _ts;
            if (diff > _threshold) {
                Util.printStack(_label + ": " + diff + " ms");
                if (_adjustThreshold) _threshold = diff;
            }
        }
    }

    public void reset()
    {
        if (_threshold != 0) {
            assert _started;
            _started = false;
            Util.l().warn(_label + " reset");
        }
    }
}
