package com.aerofs.polaris.ssmp;

import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.baseline.Threads;
import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.notification.BinaryPublisher;
import com.aerofs.polaris.notification.ManagedUpdatePublisher;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPIdentifier;
import com.aerofs.ssmp.SSMPRequest;
import com.aerofs.ssmp.SSMPResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.*;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.aerofs.polaris.Constants.DEPLOYMENT_SECRET_INJECTION_KEY;
import static com.aerofs.polaris.api.PolarisUtilities.getUpdateTopic;
import static com.aerofs.ssmp.SSMPDecoder.MAX_PAYLOAD_LENGTH;

// FIXME (AG): this is a piss-poor updater. I have another approach in mind where you can dump out the actual transforms
@Singleton
public final class SSMPPublisher implements ManagedUpdatePublisher, BinaryPublisher {

    private static final int NUM_BOSS_THREADS = 1;
    private static final int NUM_WORK_THREADS = 8;

    private static final Logger LOGGER = LoggerFactory.getLogger(SSMPPublisher.class);

    private final Timer timer = new HashedWheelTimer(Threads.newNamedThreadFactory("vk-tmr-%d"));
    private final ExecutorService addressResolverExecutor = Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-res-%d"));
    private BossPool<NioClientBoss> bossPool = new NioClientBossPool(Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-boss-%d")), NUM_BOSS_THREADS);
    private WorkerPool<NioWorker> workerPool = new NioWorkerPool(Executors.newCachedThreadPool(Threads.newNamedThreadFactory("vk-wrk-%d")), NUM_WORK_THREADS);
    private final ChannelFactory channelFactory = new NioClientSocketChannelFactory(bossPool, workerPool);
    private final SSMPConnection ssmp;

    @Inject
    public SSMPPublisher(ICertificateProvider cacert,
                         @Named(DEPLOYMENT_SECRET_INJECTION_KEY) String deploymentSecret)
    {
        InetSocketAddress lipwigAddress = InetSocketAddress.createUnresolved("lipwig.service", 8787);
        LOGGER.info("setup lipwig update publisher {}", lipwigAddress);

        this.ssmp = new SSMPConnection(deploymentSecret,
                lipwigAddress,
                timer,
                channelFactory,
                new SSLEngineFactory(Mode.Client, Platform.Desktop, null, cacert, null)
                        ::newSslHandler
        );
    }

    @Override
    public void start() throws Exception {
        this.ssmp.start();
        // noop
    }

    @Override
    public void stop() {
        ssmp.stop();
        channelFactory.shutdown();
        addressResolverExecutor.shutdownNow();
        timer.stop();
    }

    @Override
    public ListenableFuture<Void> publishUpdate(String topic, Update update) {
        SettableFuture<Void> returned = SettableFuture.create();

        LOGGER.debug("notify {}", topic);

        try {
            Futures.addCallback(
                    ssmp.request(SSMPRequest.mcast(SSMPIdentifier.fromInternal(getUpdateTopic(topic)),
                            Long.toString(update.latestLogicalTimestamp))), ssmpRequestCallback(topic, returned));
        } catch (Exception e) {
            LOGGER.warn("fail publish notification for {}", topic);
            returned.setException(e);
        }

        return returned;
    }

    @Override
    public ListenableFuture<Void> publishBinary(String topic, byte[] payload, int elementSize) {
        SettableFuture<Void> returned = SettableFuture.create();

        LOGGER.debug("notify {}", topic);

        try {
            int chunkSize = (MAX_PAYLOAD_LENGTH) / elementSize * elementSize;
            if (payload.length < MAX_PAYLOAD_LENGTH) {
                Futures.addCallback(ssmp.request(
                        SSMPRequest.mcast(SSMPIdentifier.fromInternal(topic), payload)),
                        ssmpRequestCallback(topic, returned));
            } else {
                int index = 0;
                while (payload.length - index > MAX_PAYLOAD_LENGTH) {
                    Futures.addCallback(
                            ssmp.request(SSMPRequest.mcast(SSMPIdentifier.fromInternal(topic),
                                            Arrays.copyOfRange(payload, index, index + chunkSize))),
                            ssmpRequestCallback(topic, returned));
                }
                index += chunkSize;
            }
        } catch (Exception e) {
            LOGGER.warn("fail publish notification for {}", topic);
            returned.setException(e);
        }

        return returned;
    }

    private FutureCallback<SSMPResponse> ssmpRequestCallback(String topic,
            SettableFuture<Void> returned) {
        return new FutureCallback<SSMPResponse>() {
            @Override
            public void onSuccess(@Nullable SSMPResponse result) {
                LOGGER.debug("done notify {} {}", topic, result.code);
                if (result.code == SSMPResponse.OK || result.code == SSMPResponse.NOT_FOUND) {
                    returned.set(null);
                } else {
                    returned.setException(new Exception("failed notify " + topic + " " + result.code));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LOGGER.debug("fail notify {}", topic);
                returned.setException(t);
            }
        };
    }
}
