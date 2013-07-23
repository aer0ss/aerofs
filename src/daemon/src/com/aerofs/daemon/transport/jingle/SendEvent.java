/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.MessageEvent;

import static com.google.common.base.Preconditions.checkNotNull;

class SendEvent
{
    private final ChannelBuffer _channelBuffer;
    private final byte[] _bytes;
    private final ChannelFuture _future;
    private final Channel _channel;

    SendEvent(MessageEvent event)
    {
        _channelBuffer = (ChannelBuffer)event.getMessage();
        _bytes = getByteArray(_channelBuffer);
        _channelBuffer.readerIndex(0); // reset the reader index, since we've just read all bytes.
        _future = checkNotNull(event.getFuture());
        _channel = event.getChannel();
    }

    /**
     * Note: the byte array returned here may be much larger than the actual bytes to write.
     * Always use readIndex() and readableByte() to get the actual buffer size.
     */
    byte[] buffer()
    {
        return _bytes;
    }

    int readIndex()
    {
        return _channelBuffer.readerIndex();
    }

    void markRead(int bytes)
    {
        _channelBuffer.skipBytes(bytes);
    }

    int readableBytes()
    {
        return _channelBuffer.readableBytes();
    }

    ChannelFuture getFuture()
    {
        return _future;
    }

    Channel getChannel()
    {
        return _channel;
    }

    /**
     * Gets a byte array out of a ChannelBuffer, avoiding memory copy if possible
     */
    private static byte[] getByteArray(ChannelBuffer buffer)
    {
        if (buffer.hasArray()) {
            return buffer.array();
        } else {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        }
    }
}
