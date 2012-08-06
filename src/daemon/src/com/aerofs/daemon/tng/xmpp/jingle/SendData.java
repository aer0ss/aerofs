/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

import com.aerofs.lib.async.UncancellableFuture;

final class SendData
{
    private final byte[][] _data;
    private final UncancellableFuture<Void> _completionFuture;
    private final byte[] _dataHeader;

    private int _cur = -1;

    SendData(byte[][] data, UncancellableFuture<Void> completionFuture)
    {
        this._data = data;
        this._completionFuture = completionFuture;

        int len = 0;
        for (byte[] bs : data) len += bs.length;

        this._dataHeader = Channel.writeHeader(len);
    }

    /**
     * @return null if there's no more element
     */
    byte[] current()
    {
        return _cur < 0 ? _dataHeader : (_cur == _data.length ? null : _data[_cur]);
    }

    void next()
    {
        _cur++;
    }

    UncancellableFuture<Void> getCompletionFuture()
    {
        return _completionFuture;
    }
}
