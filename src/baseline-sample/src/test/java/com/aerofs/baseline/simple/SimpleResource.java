package com.aerofs.baseline.simple;

import com.aerofs.baseline.metrics.MetricRegistries;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import org.junit.rules.ExternalResource;

public final class SimpleResource extends ExternalResource {

    private final Simple simple = new Simple();

    @Override
    protected void before() throws Throwable {
        simple.runWithConfiguration(ServerConfiguration.SIMPLE);
    }

    @Override
    protected void after() {
        // remove all registered metrics
        MetricRegistry registry = MetricRegistries.getRegistry();
        registry.removeMatching(new MetricFilter() {
            @Override
            public boolean matches(String name, Metric metric) {
                return true;
            }
        });

        // shutdown the server
        simple.shutdown();
    }
}
