/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.server;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This handles upstream messages for a given channel instance. When a channel instance closes,
 * a reconnect is queued (using the bootstrap instance provided at instantiation).
 *
 * The reconnect will retry until successful, using an exponential retry strategy up to
 * MAX_DELAY_MS milliseconds.
 *
 * Because bootstrap returns a new channel instance for each connection, we also provide
 * a channel-connected notifier. When the connect is successful, call notifier.channelConnected()
 * so the channel instance can be cached for reuse.
 */
@org.jboss.netty.channel.ChannelHandler.Sharable
public class ReconnectingClientHandler extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(ReconnectingClientHandler.class);

    // I don't _love_ using statics to hold this state, but it does apply across
    // ReconnectingClientHandler instances.
    static final long           MAX_DELAY_MS = 10 * C.SEC;
    private static AtomicInteger _connectFailures = new AtomicInteger(0);
    private static boolean      _quiescent = false;

    /**
     * Initialize the client handler.
     *
     * To reconnect, this handler expects the 'bootstrap' object to contain an option called
     * 'remoteAddress' that contains the remote socket address.
     */
    public ReconnectingClientHandler(ClientBootstrap bootstrap, IConnectNotifier notifier)
    {
        _bootstrap = bootstrap;
        _notifier = notifier;
        _timer = new HashedWheelTimer();
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        l.warn("Channel disconnected: {}", getRemoteAddress());
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        l.warn("Channel {} closed.", ctx.getChannel());
        reconnect();
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
    {
        l.warn("Connected channel {} ({}): {}", ctx.getChannel(), getRemoteAddress());
        _notifier.channelConnected(ctx.getChannel());
        resetDelay();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
    {
        l.warn("Channel {} caught exception", ctx.getChannel(), e.getCause());
        ctx.getChannel().close();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        l.info("ignoring unhandled recv {}", e.getMessage().getClass().getSimpleName());
    }

    /**
     *  Quiesce all ReconnectingClientHandler instances. This is a hack provided for testing.
     */
    static synchronized void quiesce() { _quiescent = true; }

    /**
     * Reset the quiescent-bit for all ReconnectingClientHandlers. A hack for testing.
     */
    static synchronized void init() { _quiescent = false; }

    /** Format the remote address for logging */
    InetSocketAddress getRemoteAddress()
    {
        return (InetSocketAddress) _bootstrap.getOption("remoteAddress");
    }

    /**
     * Get and increment the reconnect delay.
     * currently this uses a simple strategy of "last delay * 2".
     *
     * Marked package-private so we can use it in a unit test.
     */
    static long getNextDelay()
    {
        return Math.min(
                1 << _connectFailures.incrementAndGet(),
                MAX_DELAY_MS);
    }
    static void resetDelay()
    {
        _connectFailures.set(0);
    }

    /**
     * Create a timer task to attempt a reconnect in the future.
     */
    private void reconnect()
    {
        long delay = getNextDelay();
        l.info("Queuing reconnect to {} in {} ms", getRemoteAddress(), delay);
        _timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception
            {
                if (_quiescent) {
                    l.info("Shutdown requested, not trying to reconnect.");
                } else {
                    l.info("Attempting to reconnect...");
                    _bootstrap.connect();
                }
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private ClientBootstrap     _bootstrap;
    private IConnectNotifier    _notifier;
    private HashedWheelTimer    _timer;
}
