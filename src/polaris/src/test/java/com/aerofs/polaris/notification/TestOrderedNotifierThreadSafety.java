package com.aerofs.polaris.notification;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.baseline.logging.ConsoleLoggingConfiguration;
import com.aerofs.baseline.logging.FileLoggingConfiguration;
import com.aerofs.baseline.logging.LoggingConfiguration;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.Polaris;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.api.types.Timestamps;
import com.aerofs.polaris.api.types.TransformType;
import com.aerofs.polaris.dao.LogicalTimestamps;
import com.aerofs.polaris.dao.NotifiedTimestamps;
import com.aerofs.polaris.dao.Transforms;
import com.aerofs.polaris.dao.types.DIDTypeArgument;
import com.aerofs.polaris.dao.types.OIDTypeArgument;
import com.aerofs.polaris.dao.types.ObjectTypeArgument;
import com.aerofs.polaris.dao.types.SIDTypeArgument;
import com.aerofs.polaris.dao.types.TransformTypeArgument;
import com.aerofs.polaris.dao.types.UniqueIDTypeArgument;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

public final class TestOrderedNotifierThreadSafety {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestOrderedNotifierThreadSafety.class);

    private static final int NUM_TRANSFORM_THREADS = 10;
    private static final int NUM_TRANSFORMS_PER_THREAD = 100;

    private final Random random = new Random();
    private final Transformer[] transformers = new Transformer[NUM_TRANSFORM_THREADS];
    private final UniqueID[] stores = {SID.generate(), SID.generate(), SID.generate(), SID.generate(), SID.rootSID(UserID.fromInternal("test@aerofs.com"))};
    private final Map<UniqueID, List<Long>> notifiedStores = Maps.newHashMap();
    private UpdatePublisher publisher = Mockito.mock(UpdatePublisher.class);
    private BasicDataSource dataSource;
    private DBI dbi;
    private OrderedNotifier notifier;

    @Rule
    public MySQLDatabase database = new MySQLDatabase("test");

    @Before
    public void setup() throws Exception {
        // setup logging
        FileLoggingConfiguration file = new FileLoggingConfiguration();
        file.setEnabled(false);

        ConsoleLoggingConfiguration console = new ConsoleLoggingConfiguration();
        console.setEnabled(true);

        LoggingConfiguration logging = new LoggingConfiguration();
        logging.setLevel(Level.ALL.toString());
        logging.setFile(file);
        logging.setConsole(console);

        com.aerofs.baseline.logging.Logging.setupLogging(logging);

        // setup database
        PolarisConfiguration configuration = Configuration.loadYAMLConfigurationFromResources(Polaris.class, "polaris_test_server.yml");
        DatabaseConfiguration database = configuration.getDatabase();
        dataSource = (BasicDataSource) Databases.newDataSource(database);

        Flyway flyway = new Flyway();
        flyway.setDataSource(dataSource);
        flyway.migrate();

        // setup JDBI
        DBI dbi = Databases.newDBI(dataSource);
        dbi.registerArgumentFactory(new UniqueIDTypeArgument.UniqueIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new OIDTypeArgument.OIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new SIDTypeArgument.SIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new DIDTypeArgument.DIDTypeArgumentFactory());
        dbi.registerArgumentFactory(new ObjectTypeArgument.ObjectTypeArgumentFactory());
        dbi.registerArgumentFactory(new TransformTypeArgument.TransformTypeArgumentFactory());

        // spy on dbi
        this.dbi = Mockito.spy(dbi);

        // setup the publisher
        // fails the publish for a certain percentage of invocations
        when(publisher.publishUpdate(anyString(), Matchers.any())).then(new Answer<ListenableFuture<Void>>() {
            @Override
            public ListenableFuture<Void> answer(InvocationOnMock invocation) throws Throwable {
                int number = random.nextInt(100);

                if (number > 70) {
                    return Futures.immediateFailedFuture(new RuntimeException("publish failed"));
                } else {
                    Update update = (Update) invocation.getArguments()[1];
                    addSuccessfulNotification(update);
                    return Futures.immediateFuture(null);
                }
            }

            private void addSuccessfulNotification(Update update) {
                synchronized (TestOrderedNotifierThreadSafety.this) {
                    List<Long> notifications = notifiedStores.get(update.store);

                    if (notifications == null) {
                        notifications = Lists.newArrayList();
                        notifiedStores.put(update.store, notifications);
                    }

                    notifications.add(update.latestLogicalTimestamp);
                }
            }
        });

        // setup notifier
        notifier = new OrderedNotifier(this.dbi, publisher);
        notifier.start();

        // finally, set up request threads
        for (int i = 0; i < NUM_TRANSFORM_THREADS; i++) {
            transformers[i] = new Transformer(i);
        }

    }

    private class Transformer extends Thread {

        private Transformer(int id) {
            super("req-" + id);
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < NUM_TRANSFORMS_PER_THREAD; i++) {
                    UniqueID store = stores[random.nextInt(stores.length)];
                    long timestamp = addTransform(store);
                    notifier.notifyStoreUpdated(store, timestamp);
                }
            } catch (Exception e) {
                LOGGER.warn("fail add transform and notify publication in {}", getName());
            }
        }

        private long addTransform(UniqueID store) {

            return dbi.inTransaction((conn, status) -> {
                Transforms transforms = conn.attach(Transforms.class);
                long  logicalTimestamp = transforms.add(DID.generate(), store, store, TransformType.INSERT_CHILD, /* don't care */ 888, OID.generate(), "hello".getBytes(Charsets.UTF_8), System.currentTimeMillis(), null);

                LogicalTimestamps timestamps = conn.attach(LogicalTimestamps.class);
                timestamps.updateLatest(store, logicalTimestamp);

                return logicalTimestamp;
            });
        }
    }

    @After
    public void teardown() {
        // stop the transformers
        for (int i = 0; i < NUM_TRANSFORM_THREADS; i++) {
            try {
                transformers[i].interrupt();
                transformers[i].join(5000); // 5 seconds
            } catch (InterruptedException e) {
                LOGGER.warn("interrupted during wait for {}", transformers[i].getName());
            }
        }

        // stop the notification subsystem
        notifier.stop();

        // shutdown the db connections
        try {
            dataSource.close();
        } catch (SQLException e) {
            // noop
        }
    }

    @Test
    public void shouldPublishInOrder() throws InterruptedException {
        // start the transformers
        for (int i = 0; i < NUM_TRANSFORM_THREADS; i++) {
            transformers[i].start();
        }

        // give it enough time for all for them to finish....
        Thread.sleep(10000); // 10 seconds

        // verify that we got the updates in order
        for (Map.Entry<UniqueID, List<Long>> entry : notifiedStores.entrySet()) {
            UniqueID store = entry.getKey();
            List<Long> timestamps = entry.getValue();

            LOGGER.info("{} -> {}", store, timestamps);

            // strictly increasing timestamps for each store
            long previous = 0;
            for (long current : timestamps) {
                assertThat(current, greaterThan(previous));
                previous = current;
            }

            // check that the last notification for each store is correct
            NotifiedTimestamps notifiedTimestamps = dbi.onDemand(NotifiedTimestamps.class);
            try {
                Timestamps stored = notifiedTimestamps.getActualAndNotifiedTimestamps(store);
                assertThat(stored.databaseTimestamp, equalTo(stored.notifiedTimestamp));
                assertThat(timestamps.get(timestamps.size() - 1), equalTo(stored.databaseTimestamp));
            } finally {
                notifiedTimestamps.close();
            }
        }
    }
}