package com.aerofs.polaris.verkehr;

import com.aerofs.auth.client.shared.AeroService;
import com.aerofs.baseline.Threads;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.net.UrlEscapers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.BossPool;
import org.jboss.netty.channel.socket.nio.NioClientBoss;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorker;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.channel.socket.nio.WorkerPool;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.aerofs.baseline.Constants.SERVICE_NAME_INJECTION_KEY;
import static com.aerofs.polaris.Constants.DEPLOYMENT_SECRET_INJECTION_KEY;

// FIXME (AG): this is a piss-poor updater. I have another approach in mind where you can dump out the actual transforms
@Singleton
public final class VerkehrPublisher implements ManagedUpdatePublisher {

    private static final int NUM_BOSS_THREADS = 1;
    private static final int NUM_WORK_THREADS = 8;
    private static final byte[] EMPTY_PAYLOAD = {(byte) 0xFF};

    private static final Logger LOGGER = LoggerFactory.getLogger(VerkehrPublisher.class);

    private final Timer timer = new HashedWheelTimer(Threads.newNamedThreadFactory("vk-tmr-%d"));
    private final ExecutorService addressResolverExecutor = Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-res-%d"));
    private BossPool<NioClientBoss> bossPool = new NioClientBossPool(Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-boss-%d")), NUM_BOSS_THREADS);
    private WorkerPool<NioWorker> workerPool = new NioWorkerPool(Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-wrk-%d")), NUM_WORK_THREADS);
    private final ChannelFactory channelFactory = new NioClientSocketChannelFactory(bossPool, workerPool);
    private final VerkehrClient verkehrClient;

    @Inject
    public VerkehrPublisher(VerkehrConfiguration configuration,
                            @Named(DEPLOYMENT_SECRET_INJECTION_KEY) String deploymentSecret,
                            @Named(SERVICE_NAME_INJECTION_KEY) String serviceName)
            throws MalformedURLException
    {
        URL url = new URL(configuration.getUrl());
        String host = url.getHost();
        short port = (short) url.getPort();

        LOGGER.info("setup verkehr update publisher {}:{}", host, port);

        this.verkehrClient = VerkehrClient.create(
                host,
                port,
                configuration.getConnectTimeout(),
                configuration.getResponseTimeout(),
                () -> AeroService.getHeaderValue(serviceName, deploymentSecret),
                timer,
                addressResolverExecutor,
                channelFactory);
    }

    @Override
    public void start() throws Exception {
        // noop
    }

    @Override
    public void stop() {
        verkehrClient.shutdown();
        channelFactory.shutdown();
        addressResolverExecutor.shutdownNow();
        timer.stop();
    }

    @Override
    public ListenableFuture<Void> publishUpdate(UniqueID root) {
        LOGGER.debug("notify {}", root);

        SettableFuture<Void> returned = SettableFuture.create();

        if (LOGGER.isDebugEnabled()) {
            Futures.addCallback(returned, new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void result) {
                    LOGGER.debug("done notify {}", root);
                }

                @Override
                public void onFailure(Throwable t) {
                    LOGGER.debug("fail notify {}", root);
                }
            });
        }

        try {
            ListenableFuture<Void> publishFuture = verkehrClient.publish(UrlEscapers.urlPathSegmentEscaper().escape(PolarisUtilities.getVerkehrUpdateTopic(root.toStringFormal())), EMPTY_PAYLOAD);
            Futures.addCallback(publishFuture, new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void result) {
                    returned.set(result);
                }

                @Override
                public void onFailure(Throwable t) {
                    returned.setException(t);
                }
            });
        } catch (Exception e) {
            LOGGER.warn("fail publish notification for {}", root);
            returned.setException(e);
        }

        return returned;
    }
}
