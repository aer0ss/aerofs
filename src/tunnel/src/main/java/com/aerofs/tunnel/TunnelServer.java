package com.aerofs.tunnel;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.net.AbstractNettyServer;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.util.Timer;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aerofs.base.ssl.SSLEngineFactory.newServerFactory;

/**
 * Tunnel server: accepts connection form tunnel clients and generate virtual client sockets
 */
public class TunnelServer extends AbstractNettyServer
{
    private final UserID _user;
    private final DID _did;

    private final Timer _timer;
    private final ITunnelConnectionListener _listener;

    private static final AtomicInteger THREAD_ID_COUNTER = new AtomicInteger(0);

    public TunnelServer(InetSocketAddress addr, @Nullable IPrivateKeyProvider key,
            @Nullable ICertificateProvider cacert, UserID user, DID did, Timer timer,
            ITunnelConnectionListener listener)
    {
        super("tunnel", addr, key == null ? null : newServerFactory(key, cacert));
        _user = user;
        _did = did;
        _timer = timer;
        _listener = listener;
    }

    @Override
    protected ChannelPipeline getSpecializedPipeline()
    {
        TunnelHandler handler = new TunnelHandler(_listener, null);
        return Channels.pipeline(
                TunnelHandler.newFrameDecoder(),
                TunnelHandler.newLengthFieldPrepender(),
                NettyUtil.newCNameVerificationHandler(handler, _user, _did),
                new ChunkedWriteHandler(),
                TunnelHandler.newIdleStateHandler(_timer),
                handler
        );
    }

    @Override
    protected ServerSocketChannelFactory getServerSocketFactory()
    {
        NioServerBossPool bossPool = new NioServerBossPool(Executors.newCachedThreadPool(), 2,
                (current, proposed) -> "tb" + THREAD_ID_COUNTER.getAndIncrement());
        NioWorkerPool workerPool = new NioWorkerPool(Executors.newCachedThreadPool(), 16,
                (current, proposed) -> "tw" + THREAD_ID_COUNTER.getAndIncrement());
        return new NioServerSocketChannelFactory(bossPool, workerPool);
    }
}
