/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.IChannelData;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportUtil;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.fireChannelConnected;

public final class CNameVerifiedHandler extends SimpleChannelHandler implements CNameListener
{
    private static final Logger l = LoggerFactory.getLogger(CNameVerifiedHandler.class);

    private final HandlerMode mode;
    private final AtomicReference<Channel> channelReference = new AtomicReference<Channel>(null);

    private volatile DID expected;
    private IUnicastListener unicastListener;

    public CNameVerifiedHandler(IUnicastListener unicastListener, HandlerMode mode)
    {
        this.unicastListener = unicastListener;
        this.mode = mode;
    }

    /**
     * Sets the did that we are expecting from the remote peer. Once the cname verification completes,
     * we will match the verified did against this.
     */
    public void setExpectedRemoteDID(DID did)
    {
        checkState(mode == HandlerMode.CLIENT, "cannot set expected remote DID on server channel");
        expected = did;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkState(channelReference.compareAndSet(null, e.getChannel()), "channel already set to %s", channelReference.get());
        super.channelOpen(ctx, e);
    }

    @Override
    public void onPeerVerified(UserID user, DID did)
    {
        Channel channel = channelReference.get();

        // check that the channel is valid, and then use checkNotNull to avoid annoying IDE warnings
        checkState(channel != null);
        channel = checkNotNull(channel);

        // don't check the channel attachment type in the message!
        // the message is evaluated regardless of whether the condition is true or false
        // and can cause NPEs
        checkState(channel.getAttachment() == null, "attachment %s exists", channel.getAttachment());

        if (channel.getCloseFuture().isDone()) {
            l.warn("{} cname verification complete but {} closed", did, TransportUtil.hexify(channel));
            return;
        }

        if (mode == HandlerMode.CLIENT) {
            checkNotNull(expected);
            checkArgument(did.equals(expected), "fail DID comparison exp:%s act:%s", expected, did);
        }

        l.info("{} connected for duplex tx on {} ", did, TransportUtil.hexify(channel));
        channel.setAttachment(new ChannelData(user, did));
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception
    {
        checkState(e.getChannel().getAttachment() != null);
        checkState(e.getChannel().getAttachment() instanceof IChannelData);

        IChannelData provider = (IChannelData) e.getChannel().getAttachment();
        checkNotNull(provider.getRemoteDID());
        checkNotNull(provider.getRemoteUserID());

        ctx.getPipeline().remove(this);

        unicastListener.onDeviceConnected(provider.getRemoteDID());

        fireChannelConnected(ctx, (SocketAddress) e.getValue());
    }
}
