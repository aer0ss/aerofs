package com.aerofs.polaris;

import org.junit.rules.ExternalResource;

public final class PolarisResource extends ExternalResource {

    private final Polaris polaris = new Polaris();

    @Override
    protected void before() throws Throwable {
        polaris.runWithConfiguration(ServerConfiguration.POLARIS);
    }

    @Override
    protected void after() {
        polaris.shutdown();
    }
}
