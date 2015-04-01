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
                    c.onDisconnect(future);
                    break;
                case INTEREST_OPS:
                    // all changes to interest ops done in I/O thread of underlying physical
                    // channel to avoid synchronization and ordering headaches
                    execute(pipeline, () -> c.onInterestChanged((Integer)value, future));
                    break;
                default:
                    break;
                }
            }
        }

        @Override
        public ChannelFuture execute(ChannelPipeline pipeline, Runnable task)
        {
            VirtualChannel c = ((VirtualChannel)pipeline.getChannel());
            // execute in physical channel's I/O thread to respect Netty's threading model
            return c._tunnel._channel.getPipeline().execute(task);
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

    @Override
    public String toString()
    {
        return super.toString() + "{" + _tunnel + "}";
    }

    public int getConnectionId()
    {
        return _local.connectionId;
    }

    public TunnelHandler handler()
    {
        return _tunnel;
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
    public boolean isWritable()
    {
        return super.isWritable() && _tunnel._channel.isWritable();
    }

    @Override
    public ChannelFuture setReadable(boolean readable)
    {
        int virtual = getInternalInterestOps();
        return setInterestOps(readable ? virtual | OP_READ : virtual & ~OP_READ);
    }

    /**
     * To honor Netty's threading model, this should only be called from the channel's I/O thread
     */
    void fireDisconnected()
    {
        l.debug("disconnecting {}", this);
        _connected.set(false);
        Channels.fireChannelDisconnected(this);
        l.debug("closing {}", this);
        Channels.fireChannelClosed(this);
        setClosed();
    }

    /**
     * To honor Netty's threading model, this should only be called from the channel's I/O thread
     */
    void makeWritable(boolean writable)
    {
        boolean changed = writable ? setWritable() : setUnwritable();
        if (changed) {
            l.info("tunnel remote {} {}", writable ? "resume" : "suspend", this);
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

    private void onDisconnect(ChannelFuture future)
    {
        _tunnel.onDisconnect(this, future);
    }

    /**
     * To honor Netty's threading model, this should only be called from the channel's I/O thread
     */
    private void onInterestChanged(int ops, ChannelFuture future)
    {
        int prev = getInternalInterestOps();
        // local interest change cannot affect writability, which reflects remote interest
        ops = (ops & ~OP_WRITE) | (prev & OP_WRITE);
        setInternalInterestOps(ops);
        if (prev != ops) {
            boolean suspend = (ops & OP_READ) == 0;
            l.info("tunnel local {} {}", suspend ? "suspend" : "resume", this);
            _tunnel.onReadabilityChanged(this, suspend, future);
        } else {
            future.setSuccess();
        }
    }
}
