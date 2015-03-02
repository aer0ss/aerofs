/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.net.NettyUtil;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.MessageEvent;

import static com.google.common.base.Preconditions.checkNotNull;

final class SendEvent
{
    private final ChannelBuffer channelBuffer;
    private final byte[] bytes;
    private final ChannelFuture writeFuture;
    private final Channel channel;

    SendEvent(MessageEvent event)
    {
        channelBuffer = (ChannelBuffer) event.getMessage();
        bytes = NettyUtil.toByteArray(channelBuffer);
        channelBuffer.readerIndex(0); // reset the reader index, since we've just read all bytes.
        writeFuture = checkNotNull(event.getFuture());
        channel = event.getChannel();
    }

    /**
     * Note: the byte array returned here may be much larger than the actual bytes to write.
     * Always use readIndex() and readableByte() to get the actual buffer size.
     */
    byte[] buffer()
    {
        return bytes;
    }

    int readIndex()
    {
        return channelBuffer.readerIndex();
    }

    void markRead(int bytes)
    {
        channelBuffer.skipBytes(bytes);
    }

    int readableBytes()
    {
        return channelBuffer.readableBytes();
    }

    ChannelFuture getWriteFuture()
    {
        return writeFuture;
    }

    Channel getChannel()
    {
        return channel;
    }
}
