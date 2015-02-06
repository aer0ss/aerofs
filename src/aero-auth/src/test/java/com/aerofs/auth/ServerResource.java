package com.aerofs.auth;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.metrics.MetricRegistries;
import org.junit.rules.ExternalResource;

/**
 * JUnit rule to automatically start and shut down a test server.
 */
public final class ServerResource extends ExternalResource {

    private static final Server.ServerConfiguration CONFIGURATION = Configuration.loadYAMLConfigurationFromResourcesUncheckedThrow(Server.class, "auth_test_server.yml");

    private final Server server;

    public ServerResource(Server server) {
        this.server = server;
    }

    @Override
    protected void before() throws Throwable {
        try {
            server.runWithConfiguration(CONFIGURATION);
        } catch (Throwable t) {
            MetricRegistries.unregisterMetrics(); // (after method does not run if this method fails)
            throw t;
        }
    }

    @Override
    protected void after() {
        server.shutdown();
    }

    public String getServiceURL() {
        HttpConfiguration configuration = CONFIGURATION.getService();
        return String.format("http://%s:%d", configuration.getHost(), configuration.getPort());
    }
}
