package com.aerofs.daemon.core;

class ComState
{
    private volatile boolean _generateVersionOnNextWrite = true;

    private int _writeCount;
    private int _writeCountLast;

    public boolean preWrite_()
    {
        _writeCount++;
        return _generateVersionOnNextWrite;
    }

    void generateVersionOnNextWrite_(boolean b)
    {
        _generateVersionOnNextWrite = b;
    }

    public int getWriteCount_()
    {
        return _writeCount;
    }

    // return the difference from the current write count to the value of write
    // count when this method is called last time
    boolean hasMoreWrites_()
    {
        int last = _writeCountLast;
        _writeCountLast = _writeCount;
        // can't use '>' here as wc may overflow
        return _writeCountLast != last;
    }

    @Override
    public String toString()
    {
        return "wc " + _writeCount;
    }
}
