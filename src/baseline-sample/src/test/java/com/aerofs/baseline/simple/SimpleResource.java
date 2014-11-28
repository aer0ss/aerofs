package com.aerofs.baseline.simple;

import org.junit.rules.ExternalResource;

public final class SimpleResource extends ExternalResource {

    private final Simple simple = new Simple();

    @Override
    protected void before() throws Throwable {
        simple.runWithConfiguration(ServerConfiguration.SIMPLE);
    }

    @Override
    protected void after() {
        simple.shutdown();
    }
}
