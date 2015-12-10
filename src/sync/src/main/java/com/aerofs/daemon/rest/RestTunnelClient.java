package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.acl.EffectiveUserList;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.tunnel.ShutdownEvent;
import com.aerofs.tunnel.TunnelClient;
import com.google.inject.Inject;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.util.Timer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.aerofs.base.config.ConfigurationProperties.getIntegerProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.daemon.core.acl.EffectiveUserList.IUserListChangeListener;
import static com.aerofs.lib.NioChannelFactories.getClientChannelFactory;

public class RestTunnelClient extends TunnelClient implements IUserListChangeListener
{
    private static final int CONN_INTERVAL_IN_MS = 1000;

    private AtomicLong lastConnectionTime = new AtomicLong(Long.MAX_VALUE);

    @Inject
    public RestTunnelClient(CfgLocalUser user, CfgLocalDID did, Timer timer,
            ClientSSLEngineFactory sslEngineFactory, final RestService service, EffectiveUserList userList)
    {
        super(getStringProperty("api.tunnel.host", "api.aerofs.com"),
                getIntegerProperty("api.tunnel.port", 8084), user.get(), did.get(),
                getClientChannelFactory(), sslEngineFactory, service::getSpecializedPipeline, timer);
        userList.addListener(this);
    }

    @Override
    protected ChannelFuture connect()
    {
        ChannelFuture f = super.connect();
        lastConnectionTime.set(System.currentTimeMillis());
        return f;
    }

    @Override
    public String toString()
    {
        return "REST tunnel";
    }

    // N.B. This method is always called with core lock held.
    @Override
    public void onUserListChanged()
    {
        long currentTime = System.currentTimeMillis();
        long last = lastConnectionTime.get();
        long delay = Math.max(0, CONN_INTERVAL_IN_MS + last - currentTime);
        _timer.newTimeout(timeout -> makeNewConnection(last), delay, TimeUnit.MILLISECONDS);
    }

    /* Make a new rest tunnel connection channel. However, do that only if there exists a previous
       connection and close the previous channel with a delay of 10 seconds so as give a buffer for
       ongoing downloads.
     */
    private void makeNewConnection(long lastConnTime) {
        Channel prevChannel = channel();
        // Return if TunnelClient hasn't gotten around to establishing a connection with server yet
        // or if lastConnectionTime has changed by the time we reach here.
        if (prevChannel == null || !prevChannel.isConnected() ||
                lastConnTime != lastConnectionTime.get()) return;
        ChannelFuture f = tryConnect();

        f.addListener(future -> {
            if (future.isSuccess()) {
                _timer.newTimeout(timeout -> prevChannel.getPipeline().execute(() ->
                        prevChannel.getPipeline().sendUpstream(new ShutdownEvent(prevChannel,
                            new DefaultChannelFuture(prevChannel, false)))), 10, TimeUnit.SECONDS);
           }
        });
    }
}
