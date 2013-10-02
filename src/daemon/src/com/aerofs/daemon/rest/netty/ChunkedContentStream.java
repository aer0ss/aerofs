/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.netty;

import com.aerofs.daemon.rest.stream.ContentStream;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.stream.ChunkedInput;

/**
 * ChunkedInput for ContentStream
 */
class ChunkedContentStream implements ChunkedInput
{
    private final ContentStream _stream;

    ChunkedContentStream(ContentStream stream)
    {
        _stream = stream;
    }

    @Override
    public boolean hasNextChunk() throws Exception
    {
        return _stream.hasMoreChunk();
    }

    @Override
    public Object nextChunk() throws Exception
    {
        ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        _stream.writeChunk(new ChannelBufferOutputStream(buffer));
        return new DefaultHttpChunk(buffer);
    }

    @Override
    public boolean isEndOfInput() throws Exception
    {
        return !_stream.hasMoreChunk();
    }

    @Override
    public void close() throws Exception
    {
        _stream.close();
    }
}
