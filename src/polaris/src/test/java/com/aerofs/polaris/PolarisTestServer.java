package com.aerofs.polaris;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.metrics.MetricRegistries;
import org.junit.rules.ExternalResource;

/**
 * JUnit resource that handles the lifecycle of a
 * polaris test-server instance.
 */
public final class PolarisTestServer extends ExternalResource {

    private static final PolarisConfiguration CONFIGURATION = Configuration.loadYAMLConfigurationFromResourcesUncheckedThrow(Polaris.class, "polaris_test_server.yml");

    /**
     * @return polaris admin url
     */
    static String getAdminURL() {
        HttpConfiguration admin = CONFIGURATION.getAdmin();
        return String.format("http://%s:%d", admin.getHost(), admin.getPort());
    }

    /**
     * @return polaris service url
     */
    static String getServiceURL() {
        HttpConfiguration service = CONFIGURATION.getService();
        return String.format("http://%s:%d", service.getHost(), service.getPort());
    }

    private final Polaris server = new Polaris();

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
