package com.aerofs.lib;

import com.aerofs.base.ex.ExProtocolError;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;
import static org.jboss.netty.channel.Channels.write;

// FIXME (AG): this class is tricky to use: I think the best solution is to somehow override LengthFieldBasedFrameDecoder
public final class MagicHandler extends SimpleChannelHandler
{
    private final int _magic;

    public MagicHandler(int magic)
    {
        this._magic = magic;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        // we do our magic comparison on raw byte buffers
        if (e.getMessage() instanceof ChannelBuffer) {
            ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

            // first check if the upstream handlers actually gave us enough bytes
            // to do our work
            if (buffer.readableBytes() < 8) {
                throw new ExProtocolError("header too small siz:[exp:" + 8 + " act:" + buffer.readableBytes() + "]");
            }

            // read the magic and compare it
            int receivedMagic = buffer.readInt();
            if (receivedMagic != _magic) {
                throw new ExProtocolError("bad magic exp:" + _magic + " act:" + receivedMagic);
            }

            // now read the length (just to move the pointer along)
            buffer.readInt();
        }

        // regardless of what happened, pass the object along to be processed
        // by the rest of the pipeline
        super.messageReceived(ctx, e);
    }

    private ChannelBuffer getMagicBuffer()
    {
        ChannelBuffer magicBuffer = ChannelBuffers.buffer(4);
        magicBuffer.writeInt(_magic);
        return magicBuffer;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        ChannelBuffer magicedBuffer = wrappedBuffer(getMagicBuffer(), (ChannelBuffer) e.getMessage());
        write(ctx, e.getFuture(), magicedBuffer);
    }
}
