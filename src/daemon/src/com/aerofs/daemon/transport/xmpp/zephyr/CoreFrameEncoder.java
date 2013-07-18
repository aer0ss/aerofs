package com.aerofs.daemon.transport.xmpp.zephyr;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MSG_BYTE_ORDER;
import static com.aerofs.lib.LibParam.CORE_HEADER_LENGTH;
import static com.aerofs.lib.LibParam.CORE_MAGIC;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.buffer.ChannelBuffers.buffer;
import static org.jboss.netty.channel.Channels.write;

/**
 * Wraps an outgoing ChannelBuffer with a header consisting of a CORE_MAGIC and payload length
 * <pre>
 *   |-----------|-------------|--------------------------------------|
 *   |  MAGIC #  |   LENGTH    |            PAYLOAD                   |
 *   |  4 bytes  |   4 bytes   |    # of bytes specified in LENGTH    |
 *   |-----------|-------------|--------------------------------------|
 *</pre>
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
final class CoreFrameEncoder extends SimpleChannelDownstreamHandler
{
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        ChannelBuffer payload = (ChannelBuffer) e.getMessage();
        checkState(payload.order() == ZEPHYR_MSG_BYTE_ORDER, "bad byteorder exp:" + ZEPHYR_MSG_BYTE_ORDER + " act:" + payload.order());

        ChannelBuffer coreBuffer = buffer(CORE_HEADER_LENGTH + payload.readableBytes());
        coreBuffer.writeInt(CORE_MAGIC);
        coreBuffer.writeInt(payload.readableBytes());
        coreBuffer.writeBytes(payload);

        write(ctx, e.getFuture(), coreBuffer);
    }
}
