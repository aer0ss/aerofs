package com.aerofs.lib;

import com.aerofs.lib.cfg.Cfg;

public class Profiler {

    private boolean _started;
    private long _ts;
    private long _threshold;
    private final boolean _adjustThreshold;

    public Profiler()
    {
        _adjustThreshold = false;
        _threshold = Cfg.profilerStartingThreshold();
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
                Util.printStack("PROFILER: " + diff + " ms");
                if (_adjustThreshold) _threshold = diff;
            }
        }
    }
}
