/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.diagnosis;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.LRUCache;

public class TransportDiagnosisState
{

    public static class FloodEntry
    {
        public final long _time;
        public final long _bytes;

        public FloodEntry(long time, long bytes)
        {
            _time = time;
            _bytes = bytes;
        }
    }

    LRUCache<Integer, Long> _pingCache = new LRUCache<Integer, Long>(
            DaemonParam.TRANSPORT_DIAGNOSIS_CACHE_CAPACITY);

    // Cache size times two because two sequences are needed for each flood;
    // times another two because the same cache is used for both sending and
    // receiving the flood
    LRUCache<Integer, FloodEntry> _floodCache = new LRUCache<Integer, FloodEntry>(
            DaemonParam.TRANSPORT_DIAGNOSIS_CACHE_CAPACITY * 4);

    public synchronized Long getPing(int seq)
    {
        return _pingCache.get_(seq);
    }

    public synchronized void removePing(int seq)
    {
        _pingCache.invalidate_(seq);
    }

    public synchronized void putPing(int seq, long l)
    {
        _pingCache.put_(seq, l);
    }

    public synchronized void putFlood(int seq, FloodEntry en)
    {
        _floodCache.put_(seq, en);
    }

    public synchronized void removeFlood(int seq)
    {
        _floodCache.invalidate_(seq);
    }

    public synchronized FloodEntry getFlood(int seq)
    {
        return _floodCache.get_(seq);
    }
}
