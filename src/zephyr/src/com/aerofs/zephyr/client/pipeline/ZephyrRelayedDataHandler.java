package com.aerofs.zephyr.client.pipeline;

import com.aerofs.zephyr.client.IZephyrRelayedDataSink;
import com.aerofs.zephyr.client.exception.ExBadZephyrMessage;
import com.aerofs.zephyr.client.message.RelayedData;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

@Sharable
final class ZephyrRelayedDataHandler extends SimpleChannelUpstreamHandler
{
    private final IZephyrRelayedDataSink incomingMessageSink;

    ZephyrRelayedDataHandler(IZephyrRelayedDataSink incomingMessageSink)
    {
        this.incomingMessageSink = incomingMessageSink;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof RelayedData)) {
            throw new ExBadZephyrMessage(errorString(RelayedData.class, e.getMessage().getClass()));
        }

        incomingMessageSink.onDataReceived(e.getChannel(), ((RelayedData) e.getMessage()).getPayload());
    }

    private static String errorString(Class expected, Class actual)
    {
        return "wrong message recvd exp:" + expected.getSimpleName() + " act:" + actual.getSimpleName();
    }
}
