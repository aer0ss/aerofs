package com.aerofs.baseline.sample;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.google.common.io.Resources;
import org.junit.rules.ExternalResource;

import java.io.FileInputStream;

/**
 * JUnit resource that handles the lifecycle of a
 * baseline test-server instance.
 */
public final class SampleTestServer extends ExternalResource {

    /**
     * Loads the test-server's configuration from the test-resources directory.
     */
    private static final class ConfigurationReference {

        private static final String TEST_CONFIGURATION_FILENAME = "sample_test_server.yml";
        private static SampleConfiguration CONFIGURATION = load();

        private static SampleConfiguration load() {
            try(FileInputStream in = new FileInputStream(Resources.getResource(TEST_CONFIGURATION_FILENAME).getFile())) {
                return Configuration.loadYAMLConfigurationFromStream(Sample.class, in);
            } catch (Exception e) {
                throw new RuntimeException("failed to load configuration", e);
            }
        }
    }

    static String getAdminURL() {
        HttpConfiguration admin = ConfigurationReference.CONFIGURATION.getAdmin();
        return String.format("http://%s:%d", admin.getHost(), admin.getPort());
    }

    static String getServiceURL() {
        HttpConfiguration service = ConfigurationReference.CONFIGURATION.getService();
        return String.format("http://%s:%d", service.getHost(), service.getPort());
    }

    private final Sample server = new Sample();

    @Override
    protected void before() throws Throwable {
        server.runWithConfiguration(ConfigurationReference.CONFIGURATION);
    }

    @Override
    protected void after() {
        server.shutdown();
    }
}
