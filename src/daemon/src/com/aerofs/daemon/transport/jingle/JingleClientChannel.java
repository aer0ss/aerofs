/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.jingle.JingleStream.IJingleStreamListener;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;

import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireMessageReceived;

public class JingleClientChannel extends AbstractChannel implements IJingleStreamListener
{
    private static final Logger l = Loggers.getLogger(JingleClientChannel.class);

    // State constants. We don't use enums in order to use an AtomicInteger for state
    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    final AtomicInteger _state = new AtomicInteger(ST_OPEN);

    private final ChannelConfig _config;
    private volatile DIDAddress _localAddress;
    private volatile DIDAddress _remoteAddress;
    private volatile JingleStream _stream;
    private ChannelFuture _connectFuture;

    protected JingleClientChannel(Channel parent, ChannelFactory factory, ChannelPipeline pipeline,
            ChannelSink sink)
    {
        super(parent, factory, pipeline, sink);
        _config = new DefaultChannelConfig();
        Channels.fireChannelOpen(this);
    }

    @Override
    public ChannelConfig getConfig()
    {
        return _config;
    }

    @Override
    public boolean isOpen()
    {
        return _state.get() >= ST_OPEN;
    }

    @Override
    public boolean isBound()
    {
        return _state.get() >= ST_BOUND;
    }

    @Override
    public boolean isConnected()
    {
        return _state.get() == ST_CONNECTED;
    }

    void setBound() throws ClosedChannelException
    {
        if (!_state.compareAndSet(ST_OPEN, ST_BOUND)) {
            switch (_state.get()) {
            case ST_CLOSED:
                throw new ClosedChannelException();
            default:
                throw new ChannelException("already bound");
            }
        }
    }

    @Override
    public ChannelFuture close()
    {
        // Other Netty implementations set the state to ST_CLOSED only when the close future is
        // fired, but this is problematic because the ssl handler tries to write when we close the
        // channel and libjingle takes a long time to notice the other peer is gone and time out
        // those writes. This is why we set the state to closed here, so that upon sending we can
        // detect that we are closed and fail all pending writes right away.
        _state.set(ST_CLOSED);
        return super.close();
    }

    void setConnected()
    {
        if (_state.get() != ST_CLOSED) {
            l.info("j: connected {}", this);

            _state.set(ST_CONNECTED);

            // TODO (GS): We never fail the connect future anywhere. This is because libjingle doesn't
            // (AFAIK) offer a mechanism to tell us when connecting to a peer fails.
            if (_connectFuture != null) _connectFuture.setSuccess();
        }
    }

    void setConnectFuture(ChannelFuture future)
    {
        _connectFuture = future;
    }

    void setLocalAddress(DIDAddress address)
    {
        _localAddress = address;
    }

    @Override
    public SocketAddress getLocalAddress()
    {
        return _localAddress;
    }

    @Override
    public SocketAddress getRemoteAddress()
    {
        return _remoteAddress;
    }

    void setRemoteAddress(DIDAddress address)
    {
        _remoteAddress = address;
    }

    void setJingleDataStream(JingleStream stream)
    {
        checkState(_stream == null);
        _stream = stream;
    }

    /**
     * This is called by the channel sink after the close event has travelled all the way through
     * the bottom of the pipeline.
     */
    void onCloseEventReceived(final ChannelFuture future, final SignalThread signalThread)
    {
        if (!setClosed()) {
            return;
        }

        if (_remoteAddress != null) {
            fireChannelDisconnected(JingleClientChannel.this);
            fireChannelUnbound(JingleClientChannel.this);
        }

        fireChannelClosed(this);
        future.setSuccess();

        signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                if (_stream != null) {
                    _stream.close(new ClosedChannelException());
                    signalThread.delayedDelete_(_stream);
                }
            }

            @Override
            public void error(Exception e)
            {
            }
        });
    }

    /**
     * This is called by the channel sink when a write reaches the bottom of the pipeline and is
     * ready to be sent.
     */
    void onWriteEventReceived(final MessageEvent event, final SignalThread signalThread)
    {
        signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                _stream.send_(event);
            }

            @Override
            public void error(Exception e)
            {
                l.warn("fail send pkt: d:" + _remoteAddress + " err: " + e);
                event.getFuture().setFailure(e);
            }

            @Override
            public String toString()
            {
                return "send: d:" + _remoteAddress;
            }
        });

        // Closing the channel makes the ssl handler send a "close notify" message, and the close
        // future won't be fired until this message is written. But libjingle can take a long time
        // (10 seconds) before noticing that the other peer is gone and failing all pending
        // writes, so the close future would not fire for a long time after we closed the channel.
        // In order to fix that, we close the stream ourselves (instead of waiting for libjingle to
        // do it) if we know that we are attempting to close the channel.
        if (!isOpen() && _stream != null) {
            _stream.close(new ClosedChannelException());
        }
    }

    @Override
    public void onJingleStreamConnected(JingleStream stream)
    {
        setConnected();
        fireChannelConnected(this, getRemoteAddress());
    }

    @Override
    public void onIncomingMessage(DID did, byte[] packet)
    {
        fireMessageReceived(this, getConfig().getBufferFactory().getBuffer(packet, 0, packet.length));
    }

    @Override
    public void onJingleStreamClosed(JingleStream stream)
    {
        close();
    }

    @Override
    public String toString()
    {
        return super.toString() + " " + ((_stream != null) ? _stream.toString() : "nostream");
    }
}
