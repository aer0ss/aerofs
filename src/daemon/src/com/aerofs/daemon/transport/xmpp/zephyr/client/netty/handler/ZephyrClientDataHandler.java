package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.handler;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.WriteCompletionEvent;

import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception.ExInvalidZephyrMessage;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.IZephyrIOEventSink;
import com.aerofs.daemon.transport.xmpp.zephyr.client.netty.message.ZephyrClientMessage;

public class ZephyrClientDataHandler extends SimpleChannelUpstreamHandler {

    private final IZephyrIOEventSink _sink;

    public ZephyrClientDataHandler(IZephyrIOEventSink sink)
    {
        _sink = sink;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception
    {
        if (!(e.getMessage() instanceof ZephyrClientMessage)) {
            throw new ExInvalidZephyrMessage("Message must be a ZephyrClientMessage");
        }

        // Forward the ChannelBuffer to the data sink for processing
        _sink.onMessageReceivedFromChannel_(e.getChannel(),
                ((ZephyrClientMessage)e.getMessage()).payload);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        _sink.onChannelConnected_(e.getChannel());
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        _sink.onChannelDisconnected_(e.getChannel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        _sink.onChannelException_(e.getChannel(), new Exception(e.getCause()));
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
    {
        _sink.onChannelSendComplete_(e.getChannel(), e.getWrittenAmount());
    }

}
