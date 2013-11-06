package com.aerofs.lib;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.channel.Channels.write;

public final class MagicPrepender extends SimpleChannelHandler
{
    private final int _magic;

    public MagicPrepender(int magic)
    {
        this._magic = magic;
    }

    private ChannelBuffer getMagicBuffer()
    {
        ChannelBuffer magicBuffer = ChannelBuffers.buffer(4);
        magicBuffer.writeInt(_magic);
        return magicBuffer;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        write(ctx, e.getFuture(), wrappedBuffer(getMagicBuffer(), (ChannelBuffer)e.getMessage()));
    }
}
