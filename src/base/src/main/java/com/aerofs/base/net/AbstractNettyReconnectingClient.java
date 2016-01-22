/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base.net;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.google.common.base.Preconditions;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.TimeUnit;

/**
 * A base class for Netty clients with auto-reconnection capability
 */
public abstract class AbstractNettyReconnectingClient
{
    private static final Logger l = Loggers.getLogger(AbstractNettyReconnectingClient.class);

    private static final int MIN_RETRY_DELAY = 1;
    private static final int MAX_RETRY_DELAY = 60;

    protected final Timer _timer;

    private volatile Channel _channel;
    private volatile boolean _running;
    private volatile boolean _closeRequested;

    private int _reconnectDelay = MIN_RETRY_DELAY;

    private final ChannelFutureListener onConnect = cf -> {
        // Since in TunnelClient, we could switch from one channel to another and we register
        // listeners when we try to connect every new channel, the listeners still get notified for.
        // unused channels. Handle that.
        // Also relevant for onDisconnect listener.
        if (_channel != null && !cf.getChannel().equals(_channel)) {
            l.debug("{} open (obsoleted by {})", cf.getChannel(), _channel);
            cf.getChannel().close();
            return;
        }
        if (cf.isSuccess()) {
            l.info("{} open", AbstractNettyReconnectingClient.this);
            _reconnectDelay = MIN_RETRY_DELAY;
        } else {
            l.warn("{} failed to connect", AbstractNettyReconnectingClient.this,
                    BaseLogUtil.suppress(cf.getCause(),
                            SocketException.class, ClosedChannelException.class,
                            UnresolvedAddressException.class));
        }
    };

    private final ChannelFutureListener onDisconnect = cf -> {
        if (_channel != null && !cf.getChannel().equals(_channel)) {
            l.debug("{} closed (obsoleted by {})", cf.getChannel(), _channel);
            return;
        }
        if (_closeRequested) {
            l.info("{} closed", AbstractNettyReconnectingClient.this);
            _running = false;
        } else {
            scheduleReconnect(selfOrClosedChannelException(cf.getCause()));
        }
    };

    protected AbstractNettyReconnectingClient(Timer timer)
    {
        _timer = timer;
    }

    protected abstract ChannelFuture connect();

    /**
     * Start client with auto-reconnection
     */
    public ChannelFuture start()
    {
        Preconditions.checkState(!_running);
        _running = true;
        _reconnectDelay = MIN_RETRY_DELAY;
        return tryConnect();
    }

    protected synchronized ChannelFuture tryConnect()
    {
        if (_closeRequested) return null;
        ChannelFuture f = connect();
        _channel = f.getChannel();
        _channel.getCloseFuture().addListener(onDisconnect);
        f.addListener(onConnect);
        return f;
    }

    /**
     * Stop client and prevent auto-reconnection
     */
    public synchronized void stop()
    {
        Preconditions.checkState(_running);
        Channel c = _channel;
        _closeRequested = true;
        if (c != null) {
            c.close().awaitUninterruptibly(500, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleReconnect(Throwable e)
    {
        l.warn("{} schedule reconnect in {}s", this, _reconnectDelay,
                BaseLogUtil.suppress(e, ConnectException.class, ClosedChannelException.class));

        _timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception
            {
                if (_closeRequested) {
                    l.info("{} closed", this);
                    _running = false;
                } else {
                    l.debug("{} reconnect", this);
                    tryConnect();
                }
            }
        }, _reconnectDelay, TimeUnit.SECONDS);

        _reconnectDelay = Math.min(_reconnectDelay * 2, MAX_RETRY_DELAY);
    }

    private static @Nonnull Throwable selfOrClosedChannelException(@Nullable Throwable t)
    {
        return t == null ? new ClosedChannelException() : t;
    }

    protected @Nullable Channel channel()
    {
        return _channel;
    }

    protected abstract ChannelPipelineFactory pipelineFactory();
}
