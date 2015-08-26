/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.ids.DID;
import com.aerofs.base.net.CoreProtocolHandlers.ExBadMagicHeader;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.lib.SystemUtil;
import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.nio.channels.UnresolvedAddressException;

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

    private final ITransport transport;
    private final StreamManager streamManager;
    private final ChannelMode channelMode;

    public ChannelTeardownHandler(ITransport transport, StreamManager streamManager, ChannelMode channelMode)
    {
        checkArgument(channelMode == ChannelMode.CLIENT
                   || channelMode == ChannelMode.SERVER
                   || channelMode == ChannelMode.TWOWAY,
                      "unrecognized channel mode:%s", channelMode);

        this.transport = transport;
        this.streamManager = streamManager;
        this.channelMode = channelMode;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        ctx.getChannel().getCloseFuture().addListener(cf -> teardown(cf.getChannel()));

        super.channelOpen(ctx, e);
    }

    private void teardown(Channel channel)
    {
        DID did = getDID(channel);
        if (did != null) {
            l.debug("{} teardown {} streams for {}", did, transport.id(), TransportUtil.hexify(channel));
            TransportProtocolUtil.sessionEnded(new Endpoint(transport, did), streamManager, channelMode.closeOutbound, channelMode.closeInbound);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception
    {
        Throwable cause = NettyUtil.truncateMessageIfNecessary(e.getCause());

        if (cause instanceof Error) {
            SystemUtil.fatal(cause);
            return;
        }

        DID did = getDID(e.getChannel());

        Channel channel = e.getChannel();

        l.warn("{} closing {} because of uncaught err",
                did,
                TransportUtil.hexify(channel),
                BaseLogUtil.trim(BaseLogUtil.suppress(
                        cause,
                        ExBadMagicHeader.class,
                        ExTimeout.class,
                        ExHandshakeFailed.class,
                        UnresolvedAddressException.class,
                        IOException.class,
                        SSLException.class,
                        SSLHandshakeException.class), 5));

        channel.close();
    }

    private static @Nullable DID getDID(Channel channel)
    {
        DID did = null;
        if (channel.getAttachment() != null) {
            did = ((ChannelData) channel.getAttachment()).getRemoteDID();
        }
        return did;
    }
}
