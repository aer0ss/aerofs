/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.jingle.JingleStream.IJingleStreamListener;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.AbstractChannel;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelConfig;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.DefaultChannelConfig;
import org.jboss.netty.channel.MessageEvent;
import org.slf4j.Logger;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelOpen;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireMessageReceived;

class JingleClientChannel extends AbstractChannel implements IJingleStreamListener
{
    private static final Logger l = Loggers.getLogger(JingleClientChannel.class);

    // State constants. We don't use enums in order to use an AtomicInteger for state
    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    private final AtomicInteger _state = new AtomicInteger(ST_OPEN);
    private final AtomicBoolean _closeEventHandled = new AtomicBoolean(false);

    private final ChannelConfig _config;
    private volatile JingleAddress _localAddress;
    private volatile JingleAddress _remoteAddress;
    private volatile ChannelFuture _connectFuture;

    /**
     * IMPORTANT: only modify this on the signal thread!
     */
    private JingleStream _stream;

    protected JingleClientChannel(Channel parent, ChannelFactory factory, ChannelPipeline pipeline, ChannelSink sink)
    {
        super(parent, factory, pipeline, sink);

        _config = new DefaultChannelConfig();

        fireChannelOpen(this);
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

    void setBound() throws ChannelException
    {
        if (!_state.compareAndSet(ST_OPEN, ST_BOUND)) {
            switch (_state.get()) {
            case ST_CLOSED:
                throw new ChannelException("channel closed");
            default:
                throw new ChannelException("channel already bound");
            }
        }
    }

    @Override
    public ChannelFuture close()
    {
        l.info("{}: close channel", this);

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
        boolean connected = _state.compareAndSet(ST_OPEN, ST_CONNECTED);

        if (!connected) {
            connected = _state.compareAndSet(ST_BOUND, ST_CONNECTED);
        }

        if (!connected) {
            l.warn("{}: fail mark channel connected: previous state:{}", this, _state.get());

            // TODO (GS): We never fail the connect future anywhere
            // FIXME (AG): I terminate connect futures at close, but, really, we need a timeout
            // This is because libjingle doesn't (AFAIK) offer a
            // mechanism to tell us when connecting to a peer fails.
            if (_connectFuture != null) {
                _connectFuture.setFailure(new ChannelException("fail connect: state:" + _state.get()));
            }

            close();
        } else {
            l.trace("{}: mark channel connected", this);

            if (_connectFuture != null) {
                _connectFuture.setSuccess();
            }
        }
    }

    void setConnectFuture(ChannelFuture future)
    {
        _connectFuture = future;
    }

    void setLocalAddress(JingleAddress address)
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

    void setRemoteAddress(JingleAddress address)
    {
        _remoteAddress = address;
    }

    /**
     * Set the JingleStream for this channel
     * MUST only be called on signal thread
     */
    void setJingleStream(JingleStream stream)
    {
        checkState(_stream == null, String.format("previous stream:%s", _stream));

        _stream = stream;

        l.trace("{}: set stream:{}", this, stream);
    }

    /**
     * This is called by the channel sink after the close event has travelled all the way through
     * the bottom of the pipeline.
     */
    void onCloseEventReceived(final ChannelFuture future, final SignalThread signalThread)
    {
        l.trace("{}: close event received - initiate cleanup", this);

        // do not re-handle the close event
        // we cannot use the return value of netty's "setClosed" because
        // it simply deallocates the channel id and returns 'false' on every call.
        if (!_closeEventHandled.compareAndSet(false, true)) {
            return;
        }

        setClosed(); // simply deallocates the channel id

        if (_connectFuture != null) {
            _connectFuture.setFailure(new ChannelException("failed to connect to " + _remoteAddress));
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
                    _stream.close(new ChannelException("channel closed"));
                    signalThread.delayedDelete(_stream);
                    _stream = null;
                }
            }

            @Override
            public void error(Exception e)
            {
                l.error("{}: fail run stream close task on signal thread", JingleClientChannel.this, e);
            }

            @Override
            public String toString()
            {
                return "close: d:" + _remoteAddress;
            }
        });
    }

    /**
     * This is called by the channel sink when a write reaches the bottom of the pipeline and is
     * ready to be sent.
     */
    void onWriteEventReceived(final MessageEvent event, final SignalThread signalThread)
    {
        l.trace("{}: send data len:{}", this, ((ChannelBuffer)event.getMessage()).readableBytes());

        signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                if (_stream == null) {
                    return;
                }

                if (!isOpen()) {
                    // Closing the channel makes the ssl handler block the channelClosedEvent
                    // and send a send a "close notify" message instead. The close
                    // future won't be fired until this message is written. But libjingle
                    // can take a long time (10 seconds) before noticing that the other
                    // peer is gone before failing all pending writes, so the close future
                    // would not fire for a long time after we closed the channel.
                    // In order to fix that, we close the underlying stream ourselves
                    // (instead of waiting for libjingle to
                    // do it) if we know that we are attempting to close the channel.
                    _stream.close(new ChannelException("channel closed by self"));
                } else {
                    _stream.send(event);
                }
            }

            @Override
            public void error(Exception e)
            {
                l.warn("{}: fail send data", this, e);
                event.getFuture().setFailure(e);
            }

            @Override
            public String toString()
            {
                return "send: d:" + _remoteAddress;
            }
        });
    }

    @Override
    public void onJingleStreamConnected(JingleStream stream)
    {
        l.info("{}: stream connected", this);

        setConnected();
        fireChannelConnected(this, getRemoteAddress());
    }

    @Override
    public void onIncomingMessage(DID did, byte[] data)
    {
        l.trace("{}: recv data len:{}", this, data.length);

        fireMessageReceived(this, getConfig().getBufferFactory().getBuffer(data, 0, data.length));
    }

    @Override
    public void onJingleStreamClosed(JingleStream stream)
    {
        l.info("{}: stream closed", this);

        close();
    }

    @Override
    public String toString()
    {
        // FIXME (AG): it's possible that we'll see either null _or_ the _stream value because toString can be called from any thread
        return super.toString() + " " + ((_stream != null) ? _stream.toString() : "nostream");
    }
}
