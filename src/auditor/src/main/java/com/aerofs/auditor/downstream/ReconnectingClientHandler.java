/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.auditor.downstream;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.lib.log.LogUtil;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

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
public class ReconnectingClientHandler extends SimpleChannelHandler
{
    private static final Logger l = Loggers.getLogger(ReconnectingClientHandler.class);

    // I don't _love_ using statics to hold this state, but it does apply across
    // ReconnectingClientHandler instances.
    private static final long   EXP_WAIT_COEFF = 2L;
    private static final long   MIN_WAIT_TIME = C.SEC / 4;
    static final long           MAX_WAIT_TIME = 60 * C.SEC;
    private static long         _interval = MIN_WAIT_TIME;
    private static boolean      _quiescent = false;

    /**
     * Initialize the client handler.
     *
     * To reconnect, this handler expects the 'bootstrap' object to contain an option called
     * 'remoteAddress' that contains the remote socket address.
     */
    public ReconnectingClientHandler(Timer timer, ClientBootstrap bootstrap, AuditChannelConnectNotifier notifier)
    {
        _bootstrap = bootstrap;
        _notifier = notifier;
        _timer = timer;
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
        l.warn("Channel {} caught exception", ctx.getChannel(),
                LogUtil.suppress(e.getCause(), java.net.SocketException.class, ClosedChannelException.class));
        ctx.getChannel().close();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        l.debug("ignoring unhandled recv {}", e.getMessage().getClass().getSimpleName());
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
        long delay = _interval;
        _interval = Math.min(_interval * EXP_WAIT_COEFF, MAX_WAIT_TIME);
        return delay;
    }
    static void resetDelay()
    {
        _interval = MIN_WAIT_TIME;
    }

    /**
     * Create a timer task to attempt a reconnect in the future.
     */
    private void reconnect()
    {
        long delay = getNextDelay();
        l.info("Queuing reconnect to {} in {} ms", getRemoteAddress(), delay);
        _timer.newTimeout(timeout -> {
            if (_quiescent) {
                l.info("Shutdown requested, not trying to reconnect.");
            } else {
                l.info("Attempting to reconnect...");
                _bootstrap.connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private ClientBootstrap     _bootstrap;
    private AuditChannelConnectNotifier _notifier;
    private Timer _timer;
}
