package com.aerofs.polaris;

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.metrics.MetricRegistries;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.acl.ManagedAccessManager;
import com.aerofs.polaris.notification.ManagedNotifier;
import com.aerofs.polaris.notification.ManagedUpdatePublisher;
import com.aerofs.polaris.notification.Notifier;
import com.aerofs.polaris.notification.UpdatePublisher;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.rules.ExternalResource;
import org.mockito.Mockito;

import java.io.IOException;

/**
 * JUnit resource that handles the lifecycle of a
 * polaris test-server instance.
 */
public final class PolarisTestServer extends ExternalResource {

    //
    // configuration and urls
    //

    private static final PolarisConfiguration CONFIGURATION = Configuration.loadYAMLConfigurationFromResourcesUncheckedThrow(Polaris.class, "polaris_test_server.yml");

    public static String getAdminURL() {
        HttpConfiguration admin = CONFIGURATION.getAdmin();
        return String.format("http://%s:%d", admin.getHost(), admin.getPort());
    }

    public static String getServiceURL() {
        HttpConfiguration service = CONFIGURATION.getService();
        return String.format("http://%s:%d", service.getHost(), service.getPort());
    }

    public static String getObjectURL(UniqueID object) {
        return String.format("%s/objects/%s/", getServiceURL(), object.toStringFormal());
    }

    public static String getTransformBatchURL() {
        return String.format("%s/batch/transforms/", getServiceURL());
    }

    public static String getTransformsURL(SID root) {
        return String.format("%s/transforms/%s/", getServiceURL(), root.toStringFormal());
    }

    public static String getLocationsURL(OID object, long version) {
        return String.format("%s/objects/%s/versions/%d/locations/", getServiceURL(), object.toStringFormal(), version);
    }

    public static String getLocationURL(OID object, long version, DID device) {
        return String.format("%s/objects/%s/versions/%d/locations/%s", getServiceURL(), object.toStringFormal(), version, device.toStringFormal());
    }

    public static String getJobURL(UniqueID id) {
        return String.format("%s/jobs/%s", getServiceURL(), id.toStringFormal());
    }

    public static String getLocationBatchURL() {
        return String.format("%s/batch/locations/", getServiceURL());
    }

    public static String getTreeUrl() {
        return String.format("%s/commands/tree/", getAdminURL());
    }

    //
    // server definition and setup
    //

    private final class TestPolaris extends Polaris {

        private final ManagedUpdatePublisher publisher = Mockito.mock(ManagedUpdatePublisher.class);
        private final ManagedAccessManager accessManager = Mockito.mock(ManagedAccessManager.class);
        private final ManagedNotifier notifier = Mockito.mock(ManagedNotifier.class);

        @Override
        public void init(PolarisConfiguration configuration, Environment environment) throws Exception {
            super.init(configuration, environment);

            environment.addBinder(new AbstractBinder() {

                @Override
                protected void configure() {
                    bind(notifier).to(ManagedNotifier.class).to(Notifier.class).ranked(1);
                    bind(publisher).to(ManagedUpdatePublisher.class).to(UpdatePublisher.class).ranked(1);
                    bind(accessManager).to(ManagedAccessManager.class).to(AccessManager.class).ranked(1);
                }
            });
        }

        @Override
        protected String getDeploymentSecret(PolarisConfiguration configuration) throws IOException {
            return "aa23e7fb907fa7f839f6f418820159ab";
        }
    }

    private final TestPolaris server = new TestPolaris();

    public Notifier getNotifier() {
        return server.notifier;
    }

    public AccessManager getAccessManager() {
        return server.accessManager;
    }

    @Override
    protected void before() throws Throwable {
        try {
            server.runWithConfiguration(CONFIGURATION);
        } catch (Throwable t) {
            MetricRegistries.unregisterMetrics();
            throw t;
        }
    }

    @Override
    protected void after() {
        server.shutdown();
    }
}
