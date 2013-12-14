/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Handler attached to the end of a unicast connection to/from a remote device.
 * This handler acts as a pipeline 'cap' and:
 * <ul>
 *     <li>Closes all pending incoming/outgoing streams to the device.</li>
 *     <li>Closes the channel if any exception is thrown by a downstream handler.</li>
 * </ul>
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
public final class ChannelTeardownHandler extends SimpleChannelUpstreamHandler
{
    private static final Logger l = Loggers.getLogger(ChannelTeardownHandler.class);

    public enum ChannelMode
    {
        CLIENT(true, false),
        SERVER(false, true),
        TWOWAY(true, true);

        private final boolean closeOutbound;
        private final boolean closeInbound;

        ChannelMode(boolean closeOutbound, boolean closeInbound)
        {
            this.closeOutbound = closeOutbound;
            this.closeInbound = closeInbound;
        }
    }

    public interface ChannelDIDProvider
    {
        DID getRemoteDID();
    }

    private final ITransport transport;
    private final IBlockingPrioritizedEventSink<IEvent> sink;
    private final StreamManager streamManager;
    private final ChannelMode channelMode;

    public ChannelTeardownHandler(ITransport transport, IBlockingPrioritizedEventSink<IEvent> sink, StreamManager streamManager, ChannelMode channelMode)
    {
        checkArgument(channelMode == ChannelMode.CLIENT
                   || channelMode == ChannelMode.SERVER
                   || channelMode == ChannelMode.TWOWAY,
                      "unrecognized channel mode:%s", channelMode);

        this.transport = transport;
        this.sink = sink;
        this.streamManager = streamManager;
        this.channelMode = channelMode;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        ctx.getChannel().getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                teardown(channelFuture.getChannel());
            }
        });

        super.channelOpen(ctx, e);
    }

    private void teardown(Channel channel)
    {
        Object attachment = channel.getAttachment();

        if (attachment != null) {
            DID did = ((ChannelDIDProvider) attachment).getRemoteDID();

            l.debug("{}: teardown streams for d:{}", transport.id(), did);

            TPUtil.sessionEnded(new Endpoint(transport, did), sink, streamManager, channelMode.closeOutbound, channelMode.closeInbound);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        l.warn("closing channel because of uncaught err:{}", e.getCause().getMessage());
        e.getChannel().close();
    }
}
