package com.aerofs.daemon.core;

import java.util.concurrent.atomic.AtomicInteger;

import com.aerofs.daemon.event.fs.IWriteSession;

class ComState implements IWriteSession {

    private int _writers;
    private int _readers;
    private int _wcLast;
    private volatile boolean _generateVersionOnNextWrite = true;

    // write count. it may be greater than actual write counts due to write
    // failures after the count is incremented
    private final AtomicInteger _wc = new AtomicInteger();

    void addWriter_()
    {
        _writers++;
    }

    int removeWriter_()
    {
        return --_writers;
    }

    int getWriterCount_()
    {
        return _writers;
    }

    void addReader_()
    {
        _readers++;
    }

    int removeReader_()
    {
        return --_readers;
    }

    int getReaderCount_()
    {
        return _readers;
    }

    // N.B. multiple threads may call this method at the same time and thus all
    // get true return. this is fine.
    @Override
    public boolean preWrite()
    {
        _wc.incrementAndGet();
        return _generateVersionOnNextWrite;
    }

    void generateVersionOnNextWrite_(boolean b)
    {
        _generateVersionOnNextWrite = b;
    }

    public int getWriteCount()
    {
        return _wc.get();
    }

    // return the difference from the current write count to the value of write
    // count when this method is called last time
    boolean hasMoreWrites_()
    {
        int last = _wcLast;
        _wcLast = _wc.get();
        // can't use '>' here as wc may overflow
        return _wcLast != last;
    }

    @Override
    public String toString()
    {
        return "w " + getWriterCount_() + " r " + getReaderCount_();
    }
}
