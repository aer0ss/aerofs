package com.aerofs.polaris;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.google.common.io.Resources;
import org.junit.rules.ExternalResource;

import java.io.FileInputStream;

/**
 * JUnit resource that handles the lifecycle of a
 * polaris test-server instance.
 */
public final class PolarisTestServer extends ExternalResource {

    /**
     * Loads the polaris test-server's configuration from the test-resources directory.
     */
    private static final class ConfigurationReference {

        private static final String TEST_CONFIGURATION_FILENAME = "polaris_test_server.yml";
        private static PolarisConfiguration CONFIGURATION = load();

        private static PolarisConfiguration load() {
            try(FileInputStream in = new FileInputStream(Resources.getResource(TEST_CONFIGURATION_FILENAME).getFile())) {
                return Configuration.loadYAMLConfigurationFromStream(Polaris.class, in);
            } catch (Exception e) {
                throw new RuntimeException("failed to load configuration", e);
            }
        }
    }

    /**
     * @return polaris admin url
     */
    static String getAdminURL() {
        HttpConfiguration admin = ConfigurationReference.CONFIGURATION.getAdmin();
        return String.format("http://%s:%d", admin.getHost(), admin.getPort());
    }

    /**
     * @return polaris service url
     */
    static String getServiceURL() {
        HttpConfiguration service = ConfigurationReference.CONFIGURATION.getService();
        return String.format("http://%s:%d", service.getHost(), service.getPort());
    }

    private final Polaris server = new Polaris();

    @Override
    protected void before() throws Throwable {
        server.runWithConfiguration(ConfigurationReference.CONFIGURATION);
    }

    @Override
    protected void after() {
        server.shutdown();
    }
}
