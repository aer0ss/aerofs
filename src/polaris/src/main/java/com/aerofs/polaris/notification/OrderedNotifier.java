package com.aerofs.polaris.notification;

import com.aerofs.baseline.Threads;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.api.types.Timestamps;
import com.aerofs.polaris.dao.NotifiedTimestamps;
import com.google.common.base.Preconditions;
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
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// FIXME (AG): this code is incredibly poor and I have no time to improve it
// FIXME (AG): stripe notifications

/**
 * Naive implementation of an ordered notification system.
 */
public final class OrderedNotifier implements ManagedNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderedNotifier.class);

    private final Set<UniqueID> pending = Sets.newConcurrentHashSet();
    private final DBI dbi;
    private final UpdatePublisher publisher;
    private final ExecutorService notifyExecutor;
    private final ListeningExecutorService databaseExecutor;

    private final class NotifyState {

        private final UniqueID root;
        private @Nullable Timestamps timestamps;

        public NotifyState(UniqueID root) {
            this.root = root;
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
    }

    @Override
    public void stop() {
        LOGGER.info("stopping ordered notifier");
        notifyExecutor.shutdown();
        databaseExecutor.shutdown();
    }

    @Override
    public void notifyStoreUpdated(UniqueID root) {
        boolean added = pending.add(root);

        if (added) {
            NotifyState state = new NotifyState(root);
            publish(state);
        }
    }

    // FIXME (AG): I wish we had fibers
    // I tried to create a chain() method and by the time I was done things looked way worse than if I wrote everything inline
    private void publish(NotifyState state) {
        notifyExecutor.submit(new Callable<Void>() {

            @Override
            public Void call() {
                ListenableFuture<Timestamps> lookupFuture = databaseExecutor.submit(() -> getTimestamps(state.root));

                Futures.addCallback(lookupFuture, new FutureCallback<Timestamps>() {

                    @Override
                    public void onSuccess(@Nullable Timestamps result) {
                        Preconditions.checkNotNull(result);

                        if (!shouldNotify(state.root, result)) {
                            return;
                        }

                        state.timestamps = result;

                        ListenableFuture<Void> publishFuture = publishStoreNotification(state);
                        Futures.addCallback(publishFuture, new FutureCallback<Void>() {

                            @Override
                            public void onSuccess(@Nullable Void result) {
                                ListenableFuture<Void> updateFuture = databaseExecutor.submit(new Callable<Void>() {
                                    @Override
                                    public Void call() {
                                        updateNotifiedTimestamp(state);
                                        return null;
                                    }
                                });

                                Futures.addCallback(updateFuture, new FutureCallback<Void>() {

                                    @Override
                                    public void onSuccess(@Nullable Void result) {
                                        Timestamps timestamps = getTimestamps(state.root);
                                        if (shouldNotify(state.root, timestamps)) {
                                            publish(new NotifyState(state.root));
                                        } else {
                                            pending.remove(state.root);
                                        }

                                    }

                                    @Override
                                    public void onFailure(Throwable t) {
                                        handleFailure(state.root, t);
                                    }
                                }, notifyExecutor);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                handleFailure(state.root, t);
                            }
                        }, notifyExecutor);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        handleFailure(state.root, t);
                    }
                }, notifyExecutor);

                return null;
            }
        });
    }

    private void handleFailure(UniqueID root, Throwable t) {
        LOGGER.warn("fail publish notification for {}", root, t);
        publish(new NotifyState(root));
    }

    // stage 1
    private Timestamps getTimestamps(UniqueID root) {
        return dbi.inTransaction(TransactionIsolationLevel.READ_COMMITTED, (conn, status) -> {
            NotifiedTimestamps notifiedTimestamps = conn.attach(NotifiedTimestamps.class);

            Timestamps timestamps = notifiedTimestamps.getActualAndNotifiedTimestamps(root);
            Preconditions.checkState(timestamps.databaseTimestamp >= 0, "max timestamp not updated for %s", root);

            return timestamps;
        });
    }

    // stage 2
    private ListenableFuture<Void> publishStoreNotification(NotifyState state) {
        Preconditions.checkArgument(state.timestamps != null, "timestamps not set for %s", state.root);
        LOGGER.debug("publish {} to {}", state.timestamps.databaseTimestamp, state.root);
        return publisher.publishUpdate(PolarisUtilities.getVerkehrUpdateTopic(state.timestamps.root.toStringFormal()), new Update(state.root, state.timestamps.databaseTimestamp));
    }

    // stage 3
    private void updateNotifiedTimestamp(NotifyState state) {
        Preconditions.checkArgument(state.timestamps != null, "timestamps not set for %s", state.root);
        dbi.inTransaction(TransactionIsolationLevel.READ_COMMITTED, (conn, status) -> {
            NotifiedTimestamps timestamps = conn.attach(NotifiedTimestamps.class);
            timestamps.updateLatest(state.root, state.timestamps.databaseTimestamp);
            return null;
        });
    }

    private static boolean shouldNotify(UniqueID root, Timestamps timestamps) {
        LOGGER.debug("compare ts for {}: latest:{} notify:{}", root, timestamps.databaseTimestamp, timestamps.notifiedTimestamp);
        return timestamps.databaseTimestamp != timestamps.notifiedTimestamp;
    }
}
