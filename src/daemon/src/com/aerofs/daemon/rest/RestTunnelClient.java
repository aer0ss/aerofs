package com.aerofs.daemon.rest;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.tunnel.TunnelClient;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.lib.ChannelFactories.getClientChannelFactory;

public class RestTunnelClient extends TunnelClient
{
    private static final Logger l = Loggers.getLogger(RestTunnelClient.class);

    private final String TUNNEL_HOST = getStringProperty("api.tunnel.host", "aerofs.com");
    private final int TUNNEL_PORT = getIntegerProperty("api.tunnel.port", 8084);

    private static final int MIN_RETRY_DELAY = 1;
    private static final int MAX_RETRY_DELAY = 60;

    private final Timer _timer;

    private volatile Channel _channel;
    private volatile boolean _running;
    private volatile boolean _closeRequested;

    private int _reconnectDelay = MIN_RETRY_DELAY;

    private final ChannelFutureListener onConnect = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture cf) throws Exception
        {
            if (cf.isSuccess()) {
                l.info("REST tunnel open");
                _reconnectDelay = MIN_RETRY_DELAY;
            }
        }
    };

    private final ChannelFutureListener onDisconnect = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture cf) throws Exception
        {
            if (_closeRequested) {
                l.info("REST tunnel closed");
                _running = false;
            } else {
                scheduleReconnect(selfOrClosedChannelException(cf.getCause()));
            }
        }
    };

    @Inject
    public RestTunnelClient(CfgLocalUser user, CfgLocalDID did, Timer timer,
            ClientSSLEngineFactory sslEngineFactory, final RestService service)
    {
        super(user.get(), did.get(), getClientChannelFactory(), sslEngineFactory,
                new ChannelPipelineFactory() {
                    @Override
                    public ChannelPipeline getPipeline() throws Exception
                    {
                        return service.getSpecializedPipeline();
                    }
                }, timer);
        _timer = timer;
    }

    public ChannelFuture start()
    {
        Preconditions.checkState(!_running);
        _running = true;
        _reconnectDelay = MIN_RETRY_DELAY;
        return tryConnect();
    }

    private ChannelFuture tryConnect()
    {
        ChannelFuture f = connect(new InetSocketAddress(TUNNEL_HOST, TUNNEL_PORT));
        _channel = f.getChannel();
        _channel.getCloseFuture().addListener(onDisconnect);
        f.addListener(onConnect);
        return f;
    }

    private static @Nonnull Throwable selfOrClosedChannelException(@Nullable Throwable t)
    {
        return t == null ? new ClosedChannelException() : t;
    }

    private void scheduleReconnect(Throwable e)
    {
        l.error("REST tunnel auto-reconnect in " + _reconnectDelay + "s",
                LogUtil.suppress(e,
                        ConnectException.class,
                        ClosedChannelException.class));

        _timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception
            {
                if (_closeRequested) {
                    l.info("REST tunnel closed");
                    _running = false;
                } else {
                    tryConnect();
                }
            }
        }, _reconnectDelay, TimeUnit.SECONDS);

        _reconnectDelay = Math.min(_reconnectDelay * 2, MAX_RETRY_DELAY);
    }

    public void stop()
    {
        Preconditions.checkState(_running);
        Channel c = _channel;
        _closeRequested = true;
        if (c != null) {
            c.close().awaitUninterruptibly(500, TimeUnit.MILLISECONDS);
        }
    }
}
