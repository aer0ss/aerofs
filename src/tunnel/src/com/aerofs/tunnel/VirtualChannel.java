package com.aerofs.tunnel;

import com.aerofs.base.Loggers;
import com.aerofs.base.net.NettyUtil;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.AbstractChannelSink;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A virtual channel operating on top of a physical tunnel channel
 *
 * By design the virtual channel is created as connected to a given address and cannot be
 * reconnected after it is closed.
 *
 * Multiple virtual channels are multiplexed on top of a single physical channel, using a 32bit
 * connection id.
 */
public class VirtualChannel extends AbstractChannel
{
    private final static Logger l = Loggers.getLogger(VirtualChannel.class);

    private static class ConnectionAddress extends SocketAddress
    {
        private static final long serialVersionUID = 0L;
        public final int connectionId;

        ConnectionAddress(int id) { connectionId = id; }
        @Override public String toString() { return Integer.toString(connectionId); }
    }

    private static class Sink extends AbstractChannelSink
    {
        @Override
        public void eventSunk(ChannelPipeline pipeline, ChannelEvent e) throws Exception
        {
            final ChannelFuture future = e.getFuture();
            VirtualChannel c = ((VirtualChannel)e.getChannel());
            if (e instanceof MessageEvent) {
                c.writeOutgoingMessage(c, ((MessageEvent)e).getMessage(), future);
            } else if (e instanceof ChannelStateEvent) {
                ChannelState state = ((ChannelStateEvent)e).getState();
                Object value = ((ChannelStateEvent)e).getValue();
                switch (NettyUtil.parseDownstreamEvent(state, value)) {
                case CLOSE:
                case UNBIND:
                case DISCONNECT:
                    c.onDisconnect(c, future);
                    break;
                case INTEREST_OPS:
                    c.onInterestChanged(c, (Integer)value, future);
                    break;
                default:
                    break;
                }
            }
        }
    }

    private final TunnelHandler _tunnel;
    private final ConnectionAddress _local;
    private final AtomicBoolean _connected = new AtomicBoolean(true);

    private final ChannelConfig _config = new DefaultChannelConfig();

    protected VirtualChannel(TunnelHandler tunnel, ChannelPipeline pipeline)
    {
        super(null, null, pipeline, new Sink());
        _tunnel = tunnel;
        _local = new ConnectionAddress(getId());
    }

    protected VirtualChannel(TunnelHandler tunnel, int connectionId, ChannelPipeline pipeline)
    {
        super(null, null, pipeline, new Sink());
        _tunnel = tunnel;
        _local = new ConnectionAddress(connectionId);
    }

    public int getConnectionId()
    {
        return _local.connectionId;
    }

    @Override
    public ChannelConfig getConfig()
    {
        return _config;
    }

    @Override
    public boolean isBound()
    {
        return _connected.get();
    }

    @Override
    public boolean isConnected()
    {
        return _connected.get();
    }

    @Override
    public ChannelFuture connect(SocketAddress addr)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public SocketAddress getLocalAddress()
    {
        return _local;
    }

    @Override
    public SocketAddress getRemoteAddress()
    {
        return _tunnel._addr;
    }

    @Override
    public int getInterestOps()
    {
        /**
         * gah, Netty interest ops are so annoyingly confusing:
         *
         * OP_WRITE is set to suspend write
         * OP_READ is set to accept reads
         *
         * which complicates merging two set of ops...
         */
        int virtual = super.getInterestOps();
        int physical = _tunnel._channel.getInterestOps();

        return ((physical & virtual) & OP_READ) | ((physical | virtual) & OP_WRITE);
    }

    void fireDisconnected()
    {
        l.info("disconnecting {}", this);
        _connected.set(false);
        Channels.fireChannelDisconnected(this);
        l.info("closing {}", this);
        Channels.fireChannelClosed(this);
        setClosed();
    }

    void fireInterestChanged(int ops)
    {
        int prev = getInterestOps();
        setInterestOpsNow(ops);
        if (prev != getInterestOps()) {
            Channels.fireChannelInterestChanged(this);
        }
    }

    private void writeOutgoingMessage(VirtualChannel c, Object message, ChannelFuture future)
    {
        if (c.isConnected()) {
            _tunnel.onWrite(c, message, future);
        } else {
            future.setFailure(new ClosedChannelException());
        }
    }

    private void onDisconnect(VirtualChannel c, ChannelFuture future)
    {
        _tunnel.onDisconnect(c, future);
    }

    private void onInterestChanged(VirtualChannel c, int ops, ChannelFuture future)
    {
        _tunnel.onInterestChanged(c, ops, future);
    }
}
