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

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import org.slf4j.Logger;

import static com.aerofs.polaris.Constants.DEPLOYMENT_SECRET_INJECTION_KEY;

public class SSMPConnectionWrapper {
    private static final int NUM_BOSS_THREADS = 1;
    private static final int NUM_WORK_THREADS = 8;
    private final static Logger l = Loggers.getLogger(SSMPListener.class);

    private final Timer timer = new HashedWheelTimer(Threads.newNamedThreadFactory("vk-tmr-%d"));
    private BossPool<NioClientBoss> bossPool = new NioClientBossPool(Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-boss-%d")), NUM_BOSS_THREADS);
    private WorkerPool<NioWorker> workerPool = new NioWorkerPool(Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-wrk-%d")), NUM_WORK_THREADS);
    private final ChannelFactory channelFactory = new NioClientSocketChannelFactory(bossPool, workerPool);
    private final ICertificateProvider cacert;
    private final String deploymentSecret;
    private SSMPConnection conn = null;

    @Inject
    public SSMPConnectionWrapper(ICertificateProvider cacert,
                @Named(DEPLOYMENT_SECRET_INJECTION_KEY) String deploymentSecret)
    {
        this.cacert = cacert;
        this.deploymentSecret = deploymentSecret;
    }

    public void setup(SSMPIdentifier id) throws Exception {
        if (conn != null) {
            l.info("ssmp conn already set up");
            throw new Exception("ssmp conn already set up");
        }
        l.info("starting ssmp connection");
        conn = new SSMPConnection(id, deploymentSecret,
                InetSocketAddress.createUnresolved("lipwig.service", 8787),
                timer,
                channelFactory,
                new SSLEngineFactory(SSLEngineFactory.Mode.Client, SSLEngineFactory.Platform.Desktop, null, cacert, null)
                        ::newSslHandler);

        conn.start();
    }

    public void teardown() {
        conn.stop();
        channelFactory.shutdown();
        timer.stop();
    }

    public SSMPConnection getConn() {
        return conn;
    }
}
