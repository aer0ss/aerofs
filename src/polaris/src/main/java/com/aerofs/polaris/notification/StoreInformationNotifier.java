package com.aerofs.polaris.notification;

import com.aerofs.baseline.Threads;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * sends store information notifications. Retries on failures for up to 5
 * minutes.
 */
public final class StoreInformationNotifier
{

    private static final Logger LOGGER = LoggerFactory.getLogger(StoreInformationNotifier.class);

    private final BinaryPublisher publisher;
    private final ExecutorService notifyExecutor;

    private class TimeLimitedRetryOnFailCallback<V> implements FutureCallback<V>
    {
        private String topic;
        private byte[] info;
        private int chunkSize;
        private final long originalRequestTimeMillis;

        public TimeLimitedRetryOnFailCallback(String topic, byte[] info, int chunkSize) {
            this.topic = topic;
            this.info = info;
            this.chunkSize = chunkSize;
            originalRequestTimeMillis = System.currentTimeMillis();
        }

        @Override
        public void onSuccess(@Nullable V result) {}

        @Override
        public void onFailure(Throwable t) {
            if (System.currentTimeMillis() - originalRequestTimeMillis < 5000) {
                LOGGER.info("fail publish notification for {}", topic, t);
                publish(topic, info, chunkSize);
            }
        }
    }

    @Inject
    public StoreInformationNotifier(BinaryPublisher publisher) {
        this.publisher = publisher;
        this.notifyExecutor = MoreExecutors.listeningDecorator(
                Executors.newSingleThreadExecutor(Threads.newNamedThreadFactory("sync-notify-%d")));
    }

    public void notify(String topic, byte[] info, int elementSize) {
        publish(topic, info, elementSize);
    }

    private void publish(String topic, byte[] info, int elementSize) {
        LOGGER.debug("publish {}", topic);

        notifyExecutor.submit(() -> {
            ListenableFuture<Void> publishFuture = publisher.publishBinary(topic, info, elementSize);
            Futures.addCallback(publishFuture,
                    new TimeLimitedRetryOnFailCallback<Void>(topic, info, elementSize), notifyExecutor);
            return null;
        });
    }
}
