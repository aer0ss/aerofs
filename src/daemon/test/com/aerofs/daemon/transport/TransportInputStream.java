/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.rx.EORxEndStream;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Queues.newConcurrentLinkedQueue;

public final class TransportInputStream extends InputStream
{
    private final DID sourcedid;
    private final StreamID streamID;
    private final IBlockingPrioritizedEventSink<IEvent> transportQueue;
    private final ConcurrentLinkedQueue<InputStream> chunkInputStreamQueue = newConcurrentLinkedQueue();

    private InputStream currentChunk;
    private boolean closed;

    public TransportInputStream(DID sourcedid, StreamID streamID, IBlockingPrioritizedEventSink<IEvent> transportQueue)
    {
        this.sourcedid = sourcedid;
        this.streamID = streamID;
        this.transportQueue = transportQueue;
    }

    public void offer(InputStream chunkInputStream)
    {
        boolean accepted = chunkInputStreamQueue.offer(chunkInputStream);
        checkState(accepted);

        synchronized (this) {
            notifyAll();
        }
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

        int lengthRemaining = length;

        try {
            while(lengthRemaining > 0) {
                if (closed) {
                    throw new IOException("stream closed");
                }

                // check if there is a chunk we can read from
                if (currentChunk == null) {
                    // there's none, so pick one from the head of the queue
                    currentChunk = chunkInputStreamQueue.poll();

                    // if the queue is empty, wait until we're notified
                    if (currentChunk == null) {
                        wait();
                        // when notified, we don't know _why_,
                        // so go to the top of the loop and check
                        // the exit conditions again
                        continue;
                    }
                }

                int bytesRead = currentChunk.read(dest, offset, lengthRemaining);
                if (bytesRead < 0) { // end of stream
                    currentChunk = null;
                    continue;
                }

                offset += bytesRead;
                lengthRemaining -= bytesRead;
            }
        } catch (InterruptedException e) {
            throw new IOException("interrupted during read");
        }

        checkState(lengthRemaining == 0);

        return length;
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
        synchronized (this) {
            closed = true;
            notifyAll();
        }

        transportQueue.enqueueBlocking(new EORxEndStream(sourcedid, streamID), Prio.LO);
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
