/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.daemon.transport.jingle.JingleStream.IJingleStreamListener;
import com.aerofs.daemon.transport.jingle.SignalThread.ISignalThreadListener;
import com.aerofs.j.StreamInterface;
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
import static org.jboss.netty.channel.Channels.fireChannelBound;
import static org.jboss.netty.channel.Channels.fireChannelClosed;
import static org.jboss.netty.channel.Channels.fireChannelConnected;
import static org.jboss.netty.channel.Channels.fireChannelDisconnected;
import static org.jboss.netty.channel.Channels.fireChannelOpen;
import static org.jboss.netty.channel.Channels.fireChannelUnbound;
import static org.jboss.netty.channel.Channels.fireExceptionCaught;
import static org.jboss.netty.channel.Channels.fireMessageReceived;

/**
 * Bidirectional netty {@link org.jboss.netty.channel.Channel} that uses Jingle as the transport.
 * {@code JingleClientChannel} wraps a {@code JingleStream} and delegates all I/O tasks to it.
 */
class JingleClientChannel extends AbstractChannel implements ISignalThreadListener, IJingleStreamListener
{
    private static final Logger l = Loggers.getLogger(JingleClientChannel.class);

    private static final int ST_OPEN = 0;
    private static final int ST_BOUND = 1;
    private static final int ST_CONNECTED = 2;
    private static final int ST_CLOSED = -1;
    private final AtomicInteger state = new AtomicInteger(ST_OPEN);

    private final AtomicBoolean closeEventHandled = new AtomicBoolean(false);
    private final ChannelConfig channelConfig = new DefaultChannelConfig();
    private final SignalThread signalThread;
    private final JingleChannelWorker channelWorker;

    private volatile JingleAddress localAddress;
    private volatile JingleAddress remoteAddress;
    private volatile ChannelFuture connectFuture;

    /**
     * IMPORTANT: only modify this on the signal thread!
     */
    private JingleStream jingleStream;

    protected JingleClientChannel(
            SignalThread signalThread,
            JingleChannelWorker channelWorker,
            Channel parent,
            ChannelFactory factory,
            ChannelPipeline pipeline,
            ChannelSink sink)
    {
        super(parent, factory, pipeline, sink);

        this.signalThread = signalThread;
        this.channelWorker = channelWorker;

        signalThread.addSignalThreadListener(this);
        fireChannelOpen(JingleClientChannel.this);
    }

    @Override
    public boolean isOpen()
    {
        return state.get() >= ST_OPEN;
    }

    @Override
    public boolean isBound()
    {
        return state.get() >= ST_BOUND;
    }

    @Override
    public boolean isConnected()
    {
        return state.get() == ST_CONNECTED;
    }

    @Override
    public ChannelConfig getConfig()
    {
        return channelConfig;
    }

    @Override
    public SocketAddress getLocalAddress()
    {
        return localAddress;
    }

    @Override
    public SocketAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    void setBound(final ChannelFuture bindFuture, final JingleAddress localAddress) throws ChannelException
    {
        channelWorker.submitChannelTask(new Runnable()
        {
            @Override
            public void run()
            {
                setChannelBoundInternal(bindFuture, localAddress);
            }
        });
    }

    private void setChannelBoundInternal(ChannelFuture bindFuture, JingleAddress localAddress)
    {
        channelWorker.assertThread();

        try {
            if (!state.compareAndSet(ST_OPEN, ST_BOUND)) { // FIXME (AG): do we need a CAS here?
                switch (state.get()) {
                case ST_CLOSED:
                    throw new ChannelException("channel closed");
                default:
                    throw new IllegalStateException("channel already bound");
                }
            }
            this.localAddress = localAddress;
            bindFuture.setSuccess();
            fireChannelBound(this, this.localAddress);
        } catch (Exception e) {
            l.warn("{} fail bind over {}", getRemote(), this, e);
            bindFuture.setFailure(e);
            fireExceptionCaught(this, e);
        }
    }

    public void wrapStreamInterface(JingleAddress remoteAddress, boolean incoming, StreamInterface streamInterface)
    {
        signalThread.assertSignalThread();

        checkState(this.remoteAddress ==  null, "previous address:%s", this.remoteAddress);
        this.remoteAddress = remoteAddress;

        try {
            checkState(jingleStream == null, "previous stream:%s", jingleStream);
            jingleStream = new JingleStream(remoteAddress.getDid(), incoming, signalThread, streamInterface, this);
            l.trace("{} set stream to {}", getRemote(), jingleStream);
        } catch (Throwable t) {
            // first, close the stream if it exists
            // can't rely on the channel to clean it up, because apparently
            // either creating it or setting it within the channel failed
            if (jingleStream != null) {
                jingleStream.close(t);
                jingleStream.delete();
            }

            onClose(new ExDeviceUnavailable("failed to create jingle stream", t));
        }
    }

    void setConnectFuture(ChannelFuture future)
    {
        connectFuture = future;
    }

    @Override
    public ChannelFuture close()
    {
        l.info("{} close chanel over {}", getRemote(), this);

        // FIXME (AG): consider closing the stream early

        // Other Netty implementations set the state to ST_CLOSED only when the close future is
        // fired, but this is problematic because the ssl handler tries to write when we close the
        // channel and libjingle takes a long time to notice the other peer is gone and time out
        // those writes. This is why we set the state to closed here, so that upon sending we can
        // detect that we are closed and fail all pending writes right away.
        state.set(ST_CLOSED);

        return super.close();
    }

    /**
     * This is called by the channel sink after the close event has travelled all the way through
     * the bottom of the pipeline. This method runs <strong>all</strong> the client-channel cleanup tasks,
     * and <strong>must</strong> be called by sinks to ensure that the {@code JingleClientChannel}
     * is closed completely.
     */
    void onCloseEventReceived(final ChannelFuture future)
    {
        state.set(ST_CLOSED);

        execute(future, new Runnable()
        {
            @Override
            public void run()
            {
                // the future that's triggering the close will always be considered successful
                // this holds even if we have already processed an earlier close event
                future.setSuccess();
                onCloseInternal();
            }
        });
    }

    /**
     * Close a channel due to an exception at the
     * bottom of the channel pipeline (due to an error in the
     * channel sink, the I/O code, etc.)
     */
    void onClose(Throwable cause)
    {
        state.set(ST_CLOSED);

        l.warn("{} close channel over {} due to i/o err:{}", getRemote(), this, cause.getMessage());

        channelWorker.submitChannelTask(new Runnable()
        {
            @Override
            public void run()
            {
                onCloseInternal();
            }
        });
    }

    private void onCloseInternal()
    {
        channelWorker.assertThread();

        l.trace("{} initiate close cleanup for {}", getRemote(), this);

        // do not re-handle the close event
        // we cannot use the return value of netty's "setClosed" because
        // it simply deallocates the channel id and returns 'false' on every call.
        if (!closeEventHandled.compareAndSet(false, true)) {
            return;
        }

        setClosed(); // simply deallocates the channel id

        // terminate any connect futures that are still pending
        // if the connectFuture.setSuccess was already called, this is a noop
        if (connectFuture != null) {
            connectFuture.setFailure(new ChannelException("failed to connect to " + remoteAddress));
        }

        if (isConnected()) {
            fireChannelDisconnected(JingleClientChannel.this);
        }

        if (isBound()) {
            fireChannelUnbound(JingleClientChannel.this);
        }

        fireChannelClosed(this);

        signalThread.removeSignalThreadListener(this);
        signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                l.info("{} close channel over {}", getRemote(), this);
                teardownJingleStream(new ExDeviceUnavailable("channel closed"), true);
            }

            @Override
            public void error(Exception e)
            {
                l.error("{} fail run channel close task for {} on st", getRemote(), JingleClientChannel.this);
            }

            @Override
            public String toString()
            {
                return "close: d:" + remoteAddress;
            }
        });
    }

    /**
     * This is called by the channel sink when a write reaches the bottom of the pipeline and is
     * ready to be sent.
     */
    void onWriteEventReceived(final MessageEvent event)
    {
        l.trace("{} send {} bytes over {}", getRemote(), ((ChannelBuffer)event.getMessage()).readableBytes(), this);

        // don't check isOpen() because that will be tripped early
        // and we want to close the stream in the check below
        if (closeEventHandled.get()) {
            event.getFuture().setFailure(new ChannelException("channel closed"));
            return;
        }

        signalThread.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                if (jingleStream == null) {
                    return;
                }

                if (isOpen()) {
                    jingleStream.send(event);
                } else {
                    // Closing the channel makes the ssl handler block the channelClosedEvent
                    // and send a send a "close notify" message instead. The close
                    // future won't be fired until this message is written. But libjingle
                    // can take a long time (10 seconds) before noticing that the other
                    // peer is gone before failing all pending writes, so the close future
                    // would not fire for a long time after we closed the channel.
                    // In order to fix that, we close the underlying stream ourselves
                    // (instead of waiting for libjingle to
                    // do it) if we know that we are attempting to close the channel.
                    jingleStream.close(new ChannelException("channel closed by self"));
                }
            }

            @Override
            public void error(Exception e)
            {
                l.warn("{} fail send over {}", getRemote(), this, e);
                event.getFuture().setFailure(e);
                onClose(e);
            }

            @Override
            public String toString()
            {
                return "send: d:" + remoteAddress;
            }
        });
    }

    @Override
    public void onSignalThreadReady()
    {
        // noop for now
    }

    @Override
    public void onSignalThreadClosing()
    {
        l.info("{} st closing - terminate {}", getRemote(), this);

        teardownJingleStream(new ExTransportUnavailable("signal thread closing"), false);

        onClose(new ExTransportUnavailable("signal thread closing"));
    }

    private void teardownJingleStream(Throwable cause, boolean delayDelete)
    {
        signalThread.assertSignalThread();

        if (jingleStream != null) {
            jingleStream.close(cause);

            if (delayDelete) {
                signalThread.delayedDelete(jingleStream);
            } else {
                jingleStream.delete();
            }

            jingleStream = null;
        }
    }

    @Override
    public void onJingleStreamConnected(JingleStream stream)
    {
        l.info("{} channel connected over {}", getRemote(), this);

        boolean connected = state.compareAndSet(ST_OPEN, ST_CONNECTED);

        if (!connected) {
            connected = state.compareAndSet(ST_BOUND, ST_CONNECTED);
        }

        if (!connected) {
            l.warn("{} fail mark channel connected over {} - previous state:{}", getRemote(), this, state.get());

            // FIXME (AG): I terminate connect futures at close, but, really, we need a timeout
            // This is because libjingle doesn't (AFAIK) offer a
            // mechanism to tell us when connecting to a peer fails.
            if (connectFuture != null) {
                connectFuture.setFailure(new ChannelException("fail connect: state:" + state.get()));
            }

            onClose(new IllegalStateException("stream in inconsistent state"));
        } else {
            l.trace("{} mark channel connected over {}", getRemote(), this);

            if (connectFuture != null) {
                connectFuture.setSuccess();
            }
        }

        channelWorker.submitChannelTask(new Runnable()
        {
            @Override
            public void run()
            {
                fireChannelConnected(JingleClientChannel.this, getRemoteAddress());
            }
        });
    }

    @Override
    public void onIncomingMessage(DID did, final byte[] data)
    {
        if (!isOpen()) {
            l.warn("{} drop incoming packet from {}", did, this);
            return;
        }

        channelWorker.submitChannelTask(new Runnable()
        {
            @Override
            public void run()
            {
                fireMessageReceived(JingleClientChannel.this, getConfig().getBufferFactory().getBuffer(data, 0, data.length));
            }
        });
    }

    @Override
    public void onJingleStreamClosed(JingleStream stream)
    {
        l.info("{} channel over {} closed", getRemote(), this);

        onClose(new ExDeviceUnavailable("jingle stream closed"));
    }

    void execute(final ChannelFuture future, final Runnable task)
    {
        channelWorker.submitChannelTask(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    task.run();
                    future.setSuccess();
                } catch (Exception e) {
                    future.setFailure(e);
                }
            }
        });
    }

    private String getRemote()
    {
        JingleAddress remote = remoteAddress;
        if (remote == null) {
            return "<UNST>";
        } else {
            return remote.getDid().toString();
        }
    }

    @Override
    public String toString()
    {
        return super.toString() + " " + ((jingleStream != null) ? jingleStream.toString() : "nostream");
    }
}
