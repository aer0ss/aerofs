/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.IOutgoingStreamFeedback;
import com.aerofs.daemon.core.net.TransferStatisticsManager;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.event.net.tx.EOBeginStream;
import com.aerofs.daemon.event.net.tx.EOChunk;
import com.aerofs.daemon.event.net.tx.EOTxEndStream;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;

import java.io.IOException;
import java.io.OutputStream;

import static com.aerofs.lib.event.Prio.LO;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class TransportOutputStream extends OutputStream implements IOutgoingStreamFeedback
{
    private static final int MAX_STREAM_CHUNK_SIZE = C.KB;
    private static final int MAX_OUTSTANDING_STREAM_CHUNKS = 10;

    private final StreamID streamID;
    private final DID did;
    private final ITransport transport;
    private final IBlockingPrioritizedEventSink<IEvent> transportQueue;
    private final IIMCExecutor imce;
    private final TransferStatisticsManager transferStatisticsManager;

    private int streamChunkSeqNum = 1;
    private int chunkCount = 0;
    private boolean begun;
    private volatile boolean closed;

    public TransportOutputStream(
            DID did,
            StreamID streamID,
            ITransport transport,
            IBlockingPrioritizedEventSink<IEvent> transportQueue,
            IIMCExecutor imce,
            TransferStatisticsManager transferStatisticsManager)
    {
        this.streamID = streamID;
        this.did = did;
        this.transport = transport;
        this.transportQueue = transportQueue;
        this.imce = imce;
        this.transferStatisticsManager = transferStatisticsManager;
    }

    @Override
    public synchronized void incChunkCount()
    {
        try {
            while (chunkCount >= MAX_OUTSTANDING_STREAM_CHUNKS) {
                wait();
            }

            chunkCount++;
        } catch (InterruptedException e) {
            throw new IllegalStateException("interrupted during wait for incChunkCount");
        }
    }

    @Override
    public synchronized void decChunkCount()
    {
        chunkCount--;

        if (chunkCount == (MAX_OUTSTANDING_STREAM_CHUNKS / 2)) {
            notifyAll();
        }
    }

    @Override
    public void setFirstFailedChunk(EOChunk chunk)
    {
        closed = true;
    }

    @Override
    public synchronized void write(int i)
            throws IOException
    {
        checkArgument(i >=0 && i <= Byte.MAX_VALUE);
        writeInternal(new byte[]{(byte) i}, 0, 1);
    }

    @Override
    public synchronized void write(byte[] bytes)
            throws IOException
    {
        writeInternal(bytes, 0, bytes.length);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length)
            throws IOException
    {
        writeInternal(bytes, offset, length);
    }

    private void writeInternal(byte[] bytes, int offset, int length)
            throws IOException
    {
        checkNotNull(bytes);

        checkArgument(offset >= 0);
        checkArgument(length >= 0);
        checkArgument(length <= bytes.length - offset);

        while (length > 0) {
            if (closed) {
                throw new IOException("stream failed");
            }

            // [sigh] so much damn copying

            byte[] chunk;

            if (length > MAX_STREAM_CHUNK_SIZE) {
                chunk = new byte[MAX_STREAM_CHUNK_SIZE];
                System.arraycopy(bytes, offset, chunk, 0, MAX_STREAM_CHUNK_SIZE);

                offset += MAX_STREAM_CHUNK_SIZE;
                length -= MAX_STREAM_CHUNK_SIZE;
            } else {
                chunk = new byte[length];
                System.arraycopy(bytes, offset, chunk, 0, length);

                offset += length;
                length = 0;
            }

            // IMPORTANT: EOChunk calls incChunkCount in its constructor
            // IMPORTANT: EOBeginStream derives from EOChunk
            if (!begun) {
                transportQueue.enqueueBlocking(new EOBeginStream(streamID, this, did, chunk, transport, imce, transferStatisticsManager), LO);
                begun = true;
            } else {
                transportQueue.enqueueBlocking(new EOChunk(streamID, this, streamChunkSeqNum++, did, chunk, transport, imce, transferStatisticsManager), LO);
            }
        }
    }

    @Override
    public synchronized void flush()
            throws IOException
    {
        // noop - we send chunks immediately
    }

    @Override
    public void close()
            throws IOException
    {
        closed = true;
        transportQueue.enqueueBlocking(new EOTxEndStream(streamID, imce), Prio.LO);
    }
}
