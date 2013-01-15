package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.handler;

import com.aerofs.base.net.ZephyrConstants;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.IZephyrIOEventSink;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception.ExInvalidZephyrMessage;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.message.ZephyrServerMessage;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

public class ZephyrRegistrationHandler extends SimpleChannelUpstreamHandler {

    private final IZephyrIOEventSink _sink;

    public ZephyrRegistrationHandler(IZephyrIOEventSink eventHandler)
    {
        _sink = eventHandler;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof ZephyrServerMessage)) {
            super.messageReceived(ctx, e);
            return;
        }

        ZephyrServerMessage message = (ZephyrServerMessage) e.getMessage();
        ChannelBuffer buffer = message.payload;

        if (buffer.readableBytes() < ZephyrConstants.ZEPHYR_REG_PAYLOAD_LEN) {
            // The registration message is too short
            throw new ExInvalidZephyrMessage("Zephyr registration message too short");
        }

        // Read the zid given by the server
        int zephyrChannelId = buffer.readInt();

        // Send the registration message to the registration handler
        _sink.onChannelRegisteredWithZephyr_(e.getChannel(), zephyrChannelId);
    }

}
