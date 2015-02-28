package com.aerofs.polaris.notification;

import com.aerofs.baseline.Threads;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.api.types.Timestamps;
import com.aerofs.polaris.dao.NotifiedTimestamps;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// FIXME (AG): the notifier thread will be bogged down making db requests
// FIXME (AG): this code is very poor and I have no time to improve it

/**
 * Naive implementation of an ordered notification system.
 */
public final class OrderedNotifier implements ManagedNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderedNotifier.class);

    private final Set<UniqueID> pending = Sets.newConcurrentHashSet();
    private final DBI dbi;
    private final UpdatePublisher publisher;
    private final ExecutorService executor;

    @SuppressWarnings("unused")
    @Inject
    public OrderedNotifier(DBI dbi, UpdatePublisher publisher) {
        this(dbi, publisher, Executors.newSingleThreadExecutor(Threads.newNamedThreadFactory("on-%d")));
    }

    OrderedNotifier(DBI dbi, UpdatePublisher publisher, ExecutorService executor) {
        this.dbi = dbi;
        this.publisher = publisher;
        this.executor = executor;
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("starting ordered notifier");
    }

    @Override
    public void stop() {
        LOGGER.info("stopping ordered notifier");
        executor.shutdown();
    }

    @Override
    public void notifyStoreUpdated(UniqueID root) {
        boolean added = pending.add(root);

        if (added) {
            publish(root, null);
        }
    }

    private void publish(UniqueID root, @Nullable Timestamps timestamps) {
        executor.execute(new RetryingRunnable(root, new PublishTask(timestamps)));
    }

    private final class RetryingRunnable implements Runnable {

        private final UniqueID root;
        private final Task task;

        public RetryingRunnable(UniqueID root, Task task) {
            this.root = root;
            this.task = task;
        }

        @Override
        public void run() {
            try {
                task.execute(root);
            } catch (Exception e) {
                LOGGER.warn("fail publish notification for {}", root, e);
                publish(root, null);
            }
        }
    }

    private interface Task {

        void execute(UniqueID root) throws Exception;
    }

    private final class PublishTask implements Task {

        private @Nullable Timestamps timestamps;

        public PublishTask(@Nullable Timestamps timestamps) {
            this.timestamps = timestamps;
        }

        @Override
        public void execute(UniqueID root) throws Exception {
            timestamps = timestamps == null ? getTimestamps(root) : timestamps;

            // check if we have to notify at all - can happen if an older request processing threads wants to publish a notification
            if (!shouldNotify(timestamps)) return;

            // notify
            ListenableFuture<Void> publishFuture = publishStoreNotification(root, timestamps.databaseTimestamp);

            // when the future triggers, attempt to write the notified timestamp to the database
            publishFuture.addListener(new RetryingRunnable(root, new UpdateTask(publishFuture, timestamps.databaseTimestamp)), executor);
        }
    }

    private final class UpdateTask implements Task {

        private final ListenableFuture<Void> publishFuture;
        private final long notifiedTimestamp;

        public UpdateTask(ListenableFuture<Void> publishFuture, long notifiedTimestamp) {

            this.publishFuture = publishFuture;
            this.notifiedTimestamp = notifiedTimestamp;
        }

        @Override
        public void execute(UniqueID root) throws Exception {
            // did the publish succeed?
            publishFuture.get();

            // update the db to indicate that we notified successfully
            updateNotifiedTimestamp(root, notifiedTimestamp);

            // check if we need to notify again
            Timestamps timestamps = getTimestamps(root);
            if (shouldNotify(timestamps)) {
                publish(root, timestamps);
            } else {
                pending.remove(root);
            }
        }
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
    private ListenableFuture<Void> publishStoreNotification(UniqueID root, long latest) {
        return publisher.publishUpdate(PolarisUtilities.getVerkehrUpdateTopic(root.toStringFormal()), new Update(root, latest));
    }

    // stage 3
    private void updateNotifiedTimestamp(UniqueID root, long latest) {
        dbi.inTransaction(TransactionIsolationLevel.READ_COMMITTED, (conn, status) -> {
            NotifiedTimestamps timestamps = conn.attach(NotifiedTimestamps.class);
            timestamps.updateLatest(root, latest);
            return null;
        });
    }

    private static boolean shouldNotify(Timestamps timestamps) {
        if (timestamps.databaseTimestamp == timestamps.notifiedTimestamp) {
            LOGGER.info("{} is up to date - latest notified timestamp:{}", timestamps.notifiedTimestamp);
            return false;
        }

        return true;
    }
}
