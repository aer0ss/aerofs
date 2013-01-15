/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import com.aerofs.base.net.ZephyrConstants;
import com.aerofs.daemon.tng.xmpp.zephyr.exception.ExInvalidZephyrMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrBindRequest;
import com.aerofs.lib.Param;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;

/**
 * Wraps an outgoing ChannelBuffer with a header consisting of a magic value and payload length.
 * <pre>
 *   |-----------|-------------|--------------------------------------|
 *   |  MAGIC #  |   LENGTH    |            PAYLOAD                   |
 *   |  4 bytes  |   4 bytes   |    # of bytes specified in LENGTH    |
 *   |-----------|-------------|--------------------------------------|
 * </pre>
 */
public class ZephyrFrameEncoder implements ChannelDownstreamHandler
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
        ChannelBuffer out = null;
        if (e.getMessage() instanceof ChannelBuffer) {
            // A regular payload, must be wrapped with the Zephyr client header
            // consisting of a Zephyr Magic value and payload length

            ChannelBuffer payload = (ChannelBuffer) e.getMessage();

            out = ChannelBuffers.buffer(
                    ZephyrConstants.ZEPHYR_CLIENT_HDR_LEN + payload.readableBytes());
            out.writeInt(Param.CORE_MAGIC);
            out.writeInt(payload.readableBytes());
            out.writeBytes(payload);
        } else if (e.getMessage() instanceof ZephyrBindRequest) {
            // This is a bind request, so extract the zid and construct a
            // Zephyr server header consisting of a Core Magic value and
            // payload length
            ZephyrBindRequest request = (ZephyrBindRequest) e.getMessage();

            if (request.remoteZid == ZephyrConstants.ZEPHYR_INVALID_CHAN_ID) {
                throw new ExInvalidZephyrMessage("Attempting to bind to invalid remote ZID");
            }

            out = ChannelBuffers.buffer(ZephyrConstants.ZEPHYR_BIND_MSG_LEN);
            out.writeBytes(ZephyrConstants.ZEPHYR_MAGIC);
            out.writeInt(ZephyrConstants.ZEPHYR_BIND_PAYLOAD_LEN);
            out.writeInt(request.remoteZid);
        } else {
            throw new ExInvalidZephyrMessage("Zephyr frames can only encode ChannelBuffers");
        }

        assert out != null : ("No data to output");

        // Make sure the endianness is ok
        assert out.order() ==
                ZephyrConstants.ZEPHYR_MSG_BYTE_ORDER : ("ChannelBuffer has incorrect byte order");

        Channels.write(ctx, e.getFuture(), out);
    }

}
