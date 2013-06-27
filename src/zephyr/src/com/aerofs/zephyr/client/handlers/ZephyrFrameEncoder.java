package com.aerofs.zephyr.client.handlers;

import com.aerofs.zephyr.client.exceptions.ExBadZephyrMessage;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_BIND_MSG_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_BIND_PAYLOAD_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_CLIENT_HDR_LEN;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_INVALID_CHAN_ID;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MAGIC;
import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_MSG_BYTE_ORDER;
import static com.aerofs.lib.LibParam.CORE_MAGIC;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.write;

/**
 * Wraps an outgoing ChannelBuffer with a header consisting of a magic value
 * and payload length.
 * <pre>
 *   |-----------|-------------|--------------------------------------|
 *   |  MAGIC #  |   LENGTH    |            PAYLOAD                   |
 *   |  4 bytes  |   4 bytes   |    # of bytes specified in LENGTH    |
 *   |-----------|-------------|--------------------------------------|
 *</pre>
 */
final class ZephyrFrameEncoder implements ChannelDownstreamHandler
{
    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt)
            throws Exception
    {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        MessageEvent e = (MessageEvent) evt;
        ChannelBuffer out;
        if (e.getMessage() instanceof ChannelBuffer) { // data to relay
            ChannelBuffer payload = (ChannelBuffer) e.getMessage();

            out = ChannelBuffers.buffer(ZEPHYR_CLIENT_HDR_LEN + payload.readableBytes());
            out.writeInt(CORE_MAGIC);
            out.writeInt(payload.readableBytes());
            out.writeBytes(payload);
        } else if (e.getMessage() instanceof BindRequest) { // zephyr bind request
            BindRequest request = (BindRequest) e.getMessage();
            if (request.remoteZid == ZEPHYR_INVALID_CHAN_ID) {
                throw new ExBadZephyrMessage("attempt bind to invalid zid");
            }

            out = ChannelBuffers.buffer(ZEPHYR_BIND_MSG_LEN);
            out.writeBytes(ZEPHYR_MAGIC);
            out.writeInt(ZEPHYR_BIND_PAYLOAD_LEN);
            out.writeInt(request.remoteZid);
        } else {
            throw new ExBadZephyrMessage("Zephyr frames can only encode ChannelBuffers");
        }

        checkState(out.order() == ZEPHYR_MSG_BYTE_ORDER, "bad byteorder exp:" + ZEPHYR_MSG_BYTE_ORDER + " act:" + out.order());
        write(ctx, e.getFuture(), out);
    }
}
