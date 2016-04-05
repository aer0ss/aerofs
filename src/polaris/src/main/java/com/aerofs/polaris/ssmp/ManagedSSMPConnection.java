package com.aerofs.polaris.ssmp;

import com.aerofs.base.Loggers;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.baseline.Threads;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPIdentifier;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.*;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import com.aerofs.baseline.Managed;
import org.slf4j.Logger;

import static com.aerofs.polaris.Constants.DEPLOYMENT_SECRET_INJECTION_KEY;

@Singleton
public class ManagedSSMPConnection implements Managed {
    private static final int NUM_BOSS_THREADS = 1;
    private static final int NUM_WORK_THREADS = 8;
    private final static Logger l = Loggers.getLogger(SSMPListener.class);

    private final Timer timer = new HashedWheelTimer(Threads.newNamedThreadFactory("vk-tmr-%d"));
    private BossPool<NioClientBoss> bossPool = new NioClientBossPool(Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-boss-%d")), NUM_BOSS_THREADS);
    private WorkerPool<NioWorker> workerPool = new NioWorkerPool(Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-wrk-%d")), NUM_WORK_THREADS);
    private final ChannelFactory channelFactory = new NioClientSocketChannelFactory(bossPool, workerPool);
    public final SSMPConnection conn;

    @Inject
    public ManagedSSMPConnection(ICertificateProvider cacert,
                @Named(DEPLOYMENT_SECRET_INJECTION_KEY) String deploymentSecret)
    {
        conn = new SSMPConnection(SSMPIdentifier.fromInternal("polaris"), deploymentSecret,
                InetSocketAddress.createUnresolved("lipwig.service", 8787),
                timer,
                channelFactory,
                new SSLEngineFactory(SSLEngineFactory.Mode.Client, SSLEngineFactory.Platform.Desktop, null, cacert, null)
                        ::newSslHandler);
    }

    public ManagedSSMPConnection(SSMPConnection conn) {
        this.conn = conn;
    }

    @Override
    public void start() throws Exception {
        l.info("starting ssmp connection");
        conn.start();
    }

    @Override
    public void stop() {
        conn.stop();
        channelFactory.shutdown();
        timer.stop();
    }
}
