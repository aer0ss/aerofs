/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.C;
import com.aerofs.daemon.transport.lib.OutgoingStream;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.ids.DID;

import java.io.IOException;
import java.io.OutputStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class TransportOutputStream extends OutputStream
{
    private static final int MAX_STREAM_CHUNK_SIZE = C.KB;

    private final DID did;
    private final ITransport transport;

    private OutgoingStream outgoing;
    private volatile boolean closed;

    public TransportOutputStream(DID did, ITransport transport)
    {
        this.did = did;
        this.transport = transport;
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
            if (outgoing == null) {
                try {
                    outgoing = transport.newOutgoingStream(did);
                } catch (ExDeviceUnavailable |ExTransportUnavailable e) {
                    throw new AssertionError(e);
                }
            }
            outgoing.write(chunk);
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
        if (outgoing != null) outgoing.close();
    }
}
