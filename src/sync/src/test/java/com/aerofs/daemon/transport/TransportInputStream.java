/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.ids.DID;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkArgument;

public final class TransportInputStream extends InputStream
{
    private final DID sourcedid;
    private final StreamID streamID;
    private final IBlockingPrioritizedEventSink<IEvent> transportQueue;
    private final InputStream stream;

    public TransportInputStream(DID sourcedid, StreamID streamID, InputStream stream,
                                IBlockingPrioritizedEventSink<IEvent> transportQueue)
    {
        this.sourcedid = sourcedid;
        this.streamID = streamID;
        this.stream = stream;
        this.transportQueue = transportQueue;
    }

    @Override
    public synchronized int read()
            throws IOException
    {
        byte[] singleByte = new byte[1];
        readInternal(singleByte, 0, 1);
        return singleByte[0];
    }

    @Override
    public synchronized int read(@Nonnull byte[] dest)
            throws IOException
    {
        return readInternal(dest, 0, dest.length);
    }

    @Override
    public synchronized int read(@Nonnull byte[] dest, int offset, int length)
            throws IOException
    {
        return readInternal(dest, offset, length);
    }

    private int readInternal(byte[] dest, int offset, int length)
            throws IOException
    {
        checkArgument(offset >= 0);
        checkArgument(length >= 0);
        checkArgument(length <= dest.length - offset);
        return stream.read(dest, offset, length);
    }

    @Override
    public boolean markSupported()
    {
        return false;
    }

    @Override
    public void close()
            throws IOException
    {
        try {
            stream.close();
        } finally {
            transportQueue.enqueueBlocking(new EORxEndStream(sourcedid, streamID), Prio.LO);
        }
    }

    //--------------------------------------------------------------------------------------------//
    //
    // unsupported operations

    @Override
    public void mark(int i)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset()
            throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long skip(long l)
            throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int available()
            throws IOException
    {
        throw new UnsupportedOperationException();
    }

}
