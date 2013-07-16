package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.ex.ExProtocolError;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import static com.aerofs.lib.LibParam.CORE_HEADER_LENGTH;
import static com.aerofs.lib.LibParam.CORE_MAGIC;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.ByteOrder.BIG_ENDIAN;

final class CoreFrameDecoder extends FrameDecoder
{
    private boolean receivedHeader = false;
    private int payloadLength = -1;

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer)
            throws Exception
    {
        checkState(buffer.order().equals(BIG_ENDIAN), "bad frame order exp:" + BIG_ENDIAN + " act:" + buffer.order());

        if (!receivedHeader) {
            if (buffer.readableBytes() < CORE_HEADER_LENGTH) { // need entire header to process payload
                return null;
            }

            int messageMagic = buffer.readInt();
            if (messageMagic != CORE_MAGIC) {
                throw new ExProtocolError("bad magic exp:" + CORE_MAGIC + "act: " + messageMagic);
            }

            payloadLength = buffer.readInt();
            if (payloadLength <= 0) {
                throw new ExProtocolError("bad payload len:" + payloadLength);
            }

            receivedHeader = true;
            buffer.markReaderIndex();
        }

        // we've got the header, now let's wait for the payload

        if (buffer.readableBytes() < payloadLength) {
            buffer.resetReaderIndex(); // reset buffer position to before the header for reading for when we next receive data
            return null;
        }

        // reset state for the next set of incoming bytes

        receivedHeader = false;

        return buffer.readBytes(payloadLength);
    }
}
