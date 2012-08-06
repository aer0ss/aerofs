/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

public final class WireData
{
    private final byte[] _data;
    private final int _wirelen;

    public WireData(byte[] data, int wirelen)
    {
        this._data = data;
        this._wirelen = wirelen;
    }

    public byte[] getData_()
    {
        return _data;
    }

    public int getWirelen_()
    {
        return _wirelen;
    }
}
