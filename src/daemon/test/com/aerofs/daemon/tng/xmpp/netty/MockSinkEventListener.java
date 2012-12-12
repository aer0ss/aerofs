package com.aerofs.daemon.tng.xmpp.netty;

import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;

import java.net.SocketAddress;

/**
 * A listener that receives events similar to that of a {@link ChannelDownstreamHandler}.
 * Tests can be performed by sub-classing this class and overriding the needed
 * methods. Default implementation is to have every operation succeed.
 *
 */
public class MockSinkEventListener
{
    public void writeRequested(MessageEvent e) throws Exception
    {
        e.getFuture().setSuccess();
        Channels.fireWriteComplete(e.getChannel(), 0);
    }

    public void closeRequested(ChannelStateEvent e) throws Exception
    {
        if (e.getChannel().isConnected()) {
            Channels.fireChannelDisconnected(e.getChannel());
        }

        if (e.getChannel().isBound()) {
            Channels.fireChannelUnbound(e.getChannel());
        }

        ((MockChannel)e.getChannel()).setClosed();
        Channels.fireChannelClosed(e.getChannel());
    }

    public void bindRequested(ChannelStateEvent e) throws Exception
    {
        e.getFuture().setSuccess();
        Channels.fireChannelBound(e.getChannel(), (SocketAddress)e.getValue());
    }

    public void unbindRequested(ChannelStateEvent e) throws Exception
    {
        e.getFuture().setSuccess();
        Channels.fireChannelUnbound(e.getChannel());
    }

    public void connectRequested(ChannelStateEvent e) throws Exception
    {
        e.getFuture().setSuccess();
        Channels.fireChannelConnected(e.getChannel(), (SocketAddress)e.getValue());
    }

    public void disconnectRequested(ChannelStateEvent e) throws Exception
    {
        e.getFuture().setSuccess();
        Channels.fireChannelDisconnected(e.getChannel());
    }

    public void setInterestOpsRequested(ChannelStateEvent e) throws Exception
    {
        e.getFuture().setSuccess();
        Channels.fireChannelInterestChanged(e.getChannel());
    }

}
