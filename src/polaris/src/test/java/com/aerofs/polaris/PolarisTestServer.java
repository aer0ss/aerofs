package com.aerofs.polaris;

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.metrics.MetricRegistries;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.notification.UpdatePublisher;
import com.aerofs.polaris.sparta.ManagedAccessManager;
import com.aerofs.polaris.verkehr.ManagedUpdatePublisher;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.rules.ExternalResource;
import org.mockito.Mockito;

import javax.inject.Singleton;
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

    public static String getObjectURL(String oid) {
        return String.format("%s/objects/%s/", getServiceURL(), oid);
    }

    public static String getTransformBatchURL() {
        return String.format("%s/batch/transforms/", getServiceURL());
    }

    public static String getTransformsURL(String root) {
        return String.format("%s/transforms/%s/", getServiceURL(), root);
    }

    public static String getLocationsURL(String oid, long version) {
        return String.format("%s/objects/%s/versions/%d/locations/", getServiceURL(), oid, version);
    }

    public static String getLocationURL(String oid, long version, String device) {
        return String.format("%s/objects/%s/versions/%d/locations/%s", getServiceURL(), oid, version, device);
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

        private final ManagedAccessManager accessManager = Mockito.mock(ManagedAccessManager.class);
        private final ManagedUpdatePublisher publisher = Mockito.mock(ManagedUpdatePublisher.class);

        @Override
        public void init(PolarisConfiguration configuration, Environment environment) throws Exception {
            super.init(configuration, environment);

            environment.addBinder(new AbstractBinder() {

                @Override
                protected void configure() {
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

    public UpdatePublisher getNotifier() {
        return server.publisher;
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
