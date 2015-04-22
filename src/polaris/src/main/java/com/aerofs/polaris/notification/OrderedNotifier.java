package com.aerofs.polaris.notification;

import com.aerofs.baseline.Threads;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.api.types.Timestamps;
import com.aerofs.polaris.dao.NotifiedTimestamps;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// FIXME (RD): plenty of optimizations to be made/were made and misled, leave until they're relevant
// FIXME (AG): stripe notifications

/**
 * Naive implementation of an ordered notification system.
 * Updates for a particular store are guaranteed to always have increasing timestamps
 */
public final class OrderedNotifier implements ManagedNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderedNotifier.class);
    private static final Long IN_PROGRESS = -1L;

    private final Map<UniqueID, Long> notificationsMade = Maps.newConcurrentMap();
    private final DBI dbi;
    private final UpdatePublisher publisher;
    private final ExecutorService notifyExecutor;
    private final ListeningExecutorService databaseExecutor;

    private abstract class RetryOnFailCallback<V> implements FutureCallback<V>
    {
        private NotifyState state;

        public RetryOnFailCallback(NotifyState state)
        {
            this.state = state;
        }

        @Override
        public void onFailure(Throwable t)
        {
            handleFailure(state, t);
        }
    }

    private final class NotifyState {

        private final UniqueID store;
        private final Long updateTimestamp;

        public NotifyState(UniqueID store, Long updateTimestamp) {
            this.store = store;
            this.updateTimestamp = updateTimestamp;
        }
    }

    // FIXME (AG): specify number of threads
    @Inject
    public OrderedNotifier(DBI dbi, UpdatePublisher publisher) {
        this(dbi,
             publisher,
             MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(Threads.newNamedThreadFactory("notify-%d"))),
             MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(Constants.NUM_NOTIFICATION_DATABASE_LOOKUP_THREADS, Threads.newNamedThreadFactory("notify-db-%d"))));
    }

    OrderedNotifier(DBI dbi, UpdatePublisher publisher, ListeningExecutorService notifyExecutor, ListeningExecutorService databaseExecutor) {
        this.dbi = dbi;
        this.publisher = publisher;
        this.notifyExecutor = notifyExecutor;
        this.databaseExecutor = databaseExecutor;
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("starting ordered notifier");
        queueNeededNotifications();
    }

    private void queueNeededNotifications()
    {
        Set<Timestamps> needNotification = dbi.inTransaction((conn, status) -> {
            NotifiedTimestamps timestamps = conn.attach(NotifiedTimestamps.class);
            Set<Timestamps> results = Sets.newHashSet();
            timestamps.getStoresNeedingNotifications().forEachRemaining(results::add);
            return results;
        });

        needNotification.stream().forEach(x -> notifyStoreUpdated(x.store, x.databaseTimestamp));
    }

    @Override
    public void stop() {
        LOGGER.info("stopping ordered notifier");
        notifyExecutor.shutdown();
        databaseExecutor.shutdown();
    }

    @Override
    public void notifyStoreUpdated(UniqueID store, Long updateTimestamp) {
        if (needToSendNotification(store, updateTimestamp)) {
            NotifyState state = new NotifyState(store, updateTimestamp);
            publish(state);
        }
    }

    private boolean needToSendNotification(UniqueID store, Long timestamp) {
        boolean completed = false;
        while (!completed) {
            Long value = notificationsMade.get(store);
            if (value == null) {
                completed = (notificationsMade.putIfAbsent(store, IN_PROGRESS) == null);
            } else if (value.equals(IN_PROGRESS) || value >= timestamp) {
                return false;
            } else {
                completed = notificationsMade.replace(store, value, IN_PROGRESS);
            }
        }
        return true;
    }

    // FIXME (AG): I wish we had fibers
    // I tried to create a chain() method and by the time I was done things looked way worse than if I wrote everything inline
    private void publish(NotifyState state) {
        notifyExecutor.submit(() -> {
            ListenableFuture<Void> publishFuture = publishStoreNotification(state);
            Futures.addCallback(publishFuture, new RetryOnFailCallback<Void>(state) {

                @Override
                public void onSuccess(@Nullable Void result) {
                    ListenableFuture<Void> updateFuture = databaseExecutor.submit(() -> {
                        updateNotifiedTimestamp(state);
                        return null;
                    });

                    Futures.addCallback(updateFuture, new RetryOnFailCallback<Void>(state) {

                        @Override
                        public void onSuccess(@Nullable Void result) {
                            finishProgress(state);
                            Timestamps timestamps = getTimestamps(state.store);
                            if (shouldNotify(state.store, timestamps)) {
                                notifyStoreUpdated(state.store, timestamps.databaseTimestamp);
                            }
                        }
                    }, notifyExecutor);
                }
            }, notifyExecutor);
            return null;
        });
    }

    private void finishProgress(NotifyState state) {
        boolean successful = notificationsMade.replace(state.store, IN_PROGRESS, state.updateTimestamp);
        if (!successful) {
            throw new IllegalStateException("attempted to finish progress on a store that was not sending a notification");
        }
    }

    private void handleFailure(NotifyState state, Throwable t) {
        LOGGER.info("fail publish notification for {}", state.store, t);
        Timestamps timestamps = getTimestamps(state.store);
        publish(new NotifyState(state.store, timestamps.databaseTimestamp));
    }

    private Timestamps getTimestamps(UniqueID store) {
        return dbi.inTransaction(TransactionIsolationLevel.READ_COMMITTED, (conn, status) -> {
            NotifiedTimestamps notifiedTimestamps = conn.attach(NotifiedTimestamps.class);

            Timestamps timestamps = notifiedTimestamps.getActualAndNotifiedTimestamps(store);
            Preconditions.checkState(timestamps.databaseTimestamp >= 0, "max timestamp not updated for %s", store);

            return timestamps;
        });
    }

    private ListenableFuture<Void> publishStoreNotification(NotifyState state) {
        LOGGER.debug("publish {} to {}", state.updateTimestamp, state.store);
        return publisher.publishUpdate(state.store.toStringFormal(), new Update(state.store, state.updateTimestamp));
    }

    private void updateNotifiedTimestamp(NotifyState state) {
        dbi.inTransaction(TransactionIsolationLevel.READ_COMMITTED, (conn, status) -> {
            NotifiedTimestamps timestamps = conn.attach(NotifiedTimestamps.class);
            timestamps.updateLatest(state.store, state.updateTimestamp);
            return null;
        });
    }

    private static boolean shouldNotify(UniqueID store, Timestamps timestamps) {
        LOGGER.debug("compare ts for {}: latest:{} notify:{}", store, timestamps.databaseTimestamp, timestamps.notifiedTimestamp);
        return timestamps.databaseTimestamp != timestamps.notifiedTimestamp;
    }
}

