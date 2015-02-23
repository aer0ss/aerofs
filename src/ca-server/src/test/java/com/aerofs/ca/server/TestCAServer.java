package com.aerofs.ca.server;

import com.aerofs.auth.server.SharedSecret;
import com.aerofs.baseline.Environment;
import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.metrics.MetricRegistries;
import com.aerofs.ca.server.config.CAConfig;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.junit.rules.ExternalResource;

public class TestCAServer extends ExternalResource
{
    private static CAConfig CONFIGURATION = Configuration.loadYAMLConfigurationFromResourcesUncheckedThrow(CAService.class, "test_ca.yml");

    private final CAService server = new TestCAService();

    public static final String testDeploymentSecret = "aa23e7fb907fa7f839f6f418820159ab";

    public static String getCAUrl()
    {
        HttpConfiguration serviceConfig = CONFIGURATION.getService();
        return String.format("http://%s:%d/prod", serviceConfig.getHost(), serviceConfig.getPort());
    }

    @Override
    protected void before() throws Throwable {
        try {
            server.runWithConfiguration(CONFIGURATION);
        } catch (Exception e) {
            MetricRegistries.unregisterMetrics();
            throw e;
        }
    }

    @Override
    protected void after() {
        server.shutdown();
    }

    private class TestCAService extends CAService
    {
        @Override
        public void init(CAConfig configuration, Environment environment) throws Exception {
            super.init(configuration, environment);

            environment.addBinder(new AbstractBinder()
            {
                @Override
                protected void configure()
                {
                    SharedSecret testSecret = new SharedSecret(testDeploymentSecret);
                    bind(testSecret).to(SharedSecret.class).ranked(1);
                }
            });
        }
    }
}

