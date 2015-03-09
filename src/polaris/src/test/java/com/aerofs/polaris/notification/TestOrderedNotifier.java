package com.aerofs.polaris.notification;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.aerofs.baseline.db.Databases;
import com.aerofs.baseline.db.MySQLDatabase;
import com.aerofs.baseline.logging.ConsoleLoggingConfiguration;
import com.aerofs.baseline.logging.FileLoggingConfiguration;
import com.aerofs.baseline.logging.LoggingConfiguration;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.Polaris;
import com.aerofs.polaris.PolarisConfiguration;
import com.aerofs.polaris.api.notification.Update;
import com.aerofs.polaris.dao.types.DIDTypeArgument;
import com.aerofs.polaris.dao.types.OIDTypeArgument;
import com.aerofs.polaris.dao.types.ObjectTypeArgument;
import com.aerofs.polaris.dao.types.SIDTypeArgument;
import com.aerofs.polaris.dao.types.TransformTypeArgument;
import com.aerofs.polaris.dao.types.UniqueIDTypeArgument;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.flywaydb.core.Flyway;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.skife.jdbi.v2.DBI;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import static com.aerofs.polaris.notification.NotifierUtilities.getLatestLogicalTimestamp;
import static com.aerofs.polaris.notification.NotifierUtilities.getLatestNotifiedLogicalTimestamp;
import static com.aerofs.polaris.notification.NotifierUtilities.getVerkehrUpdateTopic;
import static com.aerofs.polaris.notification.NotifierUtilities.setLatestLogicalTimestamp;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestOrderedNotifier {

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

        // spy on it
        this.dbi = Mockito.spy(dbi);

        // setup notifier
        notifier = new OrderedNotifier(this.dbi, publisher, MoreExecutors.sameThreadExecutor(), MoreExecutors.listeningDecorator(MoreExecutors.sameThreadExecutor())); // tasks executed inline
        notifier.start();
    }

    @After
    public void teardown() {
        notifier.stop();

        try {
            dataSource.close();
        } catch (SQLException e) {
            // noop
        }
    }

    @Test
    public void shouldPublishNotificationAndUpdateDatabaseTablesWhenStoreUpdated() {
        // set the logical timestamp associated with this store
        UniqueID root = UniqueID.generate();
        setLatestLogicalTimestamp(dbi, root, 3024);

        // future to be returned when the update is published
        SettableFuture<Void> future = SettableFuture.create();
        when(publisher.publishUpdate(anyString(), any(Update.class))).thenReturn(future);

        notifier.notifyStoreUpdated(root); // <--- CALL

        // check that we attempted to publish the update
        verify(publisher).publishUpdate(eq(getVerkehrUpdateTopic(root)), eq(new Update(root, 3024)));

        // publish succeeds!
        future.set(null);

        // database updated
        assertThat(getLatestLogicalTimestamp(dbi, root), equalTo(3024L));
        assertThat(getLatestNotifiedLogicalTimestamp(dbi, root), equalTo(3024L));
    }

    @Test
    public void shouldRetryPublishIfInitialPublishFails() {
        // mock out the publisher - return two different futures - future0 for the first call, future1 for the second
        SettableFuture<Void> future0 = SettableFuture.create();
        SettableFuture<Void> future1 = SettableFuture.create();
        when(publisher.publishUpdate(anyString(), any(Update.class))).thenReturn(future0).thenReturn(future1);

        // set the logical timestamp associated with this store
        UniqueID root = UniqueID.generate();
        setLatestLogicalTimestamp(dbi, root, 1983);

        notifier.notifyStoreUpdated(root); // <--- CALL

        // first time around the publish fails :(
        future0.setException(new RuntimeException("publish failed"));

        // we haven't changed the db
        assertThat(getLatestLogicalTimestamp(dbi, root), equalTo(1983L));
        assertThat(getLatestNotifiedLogicalTimestamp(dbi, root), equalTo(-1L));

        // second time around the publish succeeds
        future1.set(null);

        // check that we actually called the publisher
        verify(publisher, times(2)).publishUpdate(eq(getVerkehrUpdateTopic(root)), eq(new Update(root, 1983))); // first call (unsuccessful), second call succeeds

        // database updated
        assertThat(getLatestLogicalTimestamp(dbi, root), equalTo(1983L));
        assertThat(getLatestNotifiedLogicalTimestamp(dbi, root), equalTo(1983L));
    }

    @Test
    public void shouldRetryPublishWithLatestTimestampIfInitialPublishFails() {
        // mock out the publisher - return two different futures - future0 for the first call, future1 for the second
        SettableFuture<Void> future0 = SettableFuture.create();
        SettableFuture<Void> future1 = SettableFuture.create();
        when(publisher.publishUpdate(anyString(), any(Update.class))).thenReturn(future0).thenReturn(future1);

        // set the logical timestamp associated with this store
        UniqueID root = UniqueID.generate();
        setLatestLogicalTimestamp(dbi, root, 1983);

        notifier.notifyStoreUpdated(root); // <--- CALL

        // now, modify the logical timestamp associated with this store again
        // note that it's *this* timestamp that should be picked up with the second call
        setLatestLogicalTimestamp(dbi, root, 2005);

        // first time around the publish fails :(
        future0.setException(new RuntimeException("publish failed"));

        // we haven't changed the db
        assertThat(getLatestLogicalTimestamp(dbi, root), equalTo(2005L));
        assertThat(getLatestNotifiedLogicalTimestamp(dbi, root), equalTo(-1L));

        // second time around the publish succeeds
        future1.set(null);

        // check that we actually called the publisher
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(publisher, times(2)).publishUpdate(eq(getVerkehrUpdateTopic(root)), captor.capture()); // first call (unsuccessful), second call succeeds
        List<Update> updates = captor.getAllValues();
        assertThat(updates.get(0), equalTo(new Update(root, 1983))); // first call was made with the initial value at the time of the publish call
        assertThat(updates.get(1), equalTo(new Update(root, 2005))); // second call was made with the second value which existed at the time of the publish retry

        // database updated
        assertThat(getLatestLogicalTimestamp(dbi, root), equalTo(2005L));
        assertThat(getLatestNotifiedLogicalTimestamp(dbi, root), equalTo(2005L));
    }

    @Test
    public void shouldRetryPublishIfFirstDatabaseCallFails() throws Exception {
        // set the logical timestamp associated with this store
        UniqueID root = UniqueID.generate();
        setLatestLogicalTimestamp(dbi, root, 2918);

        // publish should always succeed
        when(publisher.publishUpdate(anyString(), any(Update.class))).thenReturn(Futures.immediateFuture(null));

        // first db call should fail, the second should succeed
        when(dbi.open()).thenThrow(new RuntimeException("first db call fails")).thenCallRealMethod();

        notifier.notifyStoreUpdated(root); // <--- CALL

        // database should be updated by the end of this
        assertThat(getLatestLogicalTimestamp(dbi,root), equalTo(2918L));
        assertThat(getLatestNotifiedLogicalTimestamp(dbi, root), equalTo(2918L));
    }

    @Test
    public void shouldRetryPublishIfSecondDatabaseCallFails() throws Exception {
        // set the logical timestamp associated with this store
        UniqueID root = UniqueID.generate();
        setLatestLogicalTimestamp(dbi, root, 2918);

        // publish should always succeed
        when(publisher.publishUpdate(anyString(), any(Update.class))).thenReturn(Futures.immediateFuture(null));

        // first db call should succeed, the second should fail, and following should succeed
        when(dbi.open()).thenCallRealMethod().thenThrow(new RuntimeException("second db call fails")).thenCallRealMethod();

        notifier.notifyStoreUpdated(root); // <--- CALL

        // database should be updated by the end of this
        assertThat(getLatestLogicalTimestamp(dbi, root), equalTo(2918L));
        assertThat(getLatestNotifiedLogicalTimestamp(dbi, root), equalTo(2918L));
    }


}