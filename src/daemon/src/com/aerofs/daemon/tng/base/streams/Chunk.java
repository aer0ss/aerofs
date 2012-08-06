/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import java.io.ByteArrayInputStream;

public final class Chunk
{
    private final ByteArrayInputStream _chunkIs;
    private final int _seqnum;
    private final int _wirelen;

    public Chunk(int seqnum, ByteArrayInputStream chunkIs, int wirelen)
    {
        this._chunkIs = chunkIs;
        this._seqnum = seqnum;
        this._wirelen = wirelen;
    }

    public ByteArrayInputStream getChunkIs_()
    {
        return _chunkIs;
    }

    public int getSeqnum_()
    {
        return _seqnum;
    }

    public int getWirelen_()
    {
        return _wirelen;
    }
}
