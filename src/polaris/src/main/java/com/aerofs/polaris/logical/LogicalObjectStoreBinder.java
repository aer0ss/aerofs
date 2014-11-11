package com.aerofs.polaris.logical;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.ws.rs.ext.Provider;

@Provider
public final class LogicalObjectStoreBinder extends AbstractBinder {

    private final LogicalObjectStore logicalObjectStore;

    public LogicalObjectStoreBinder(LogicalObjectStore logicalObjectStore) {
        this.logicalObjectStore = logicalObjectStore;
    }

    @Override
    protected void configure() {
        bind(logicalObjectStore).to(LogicalObjectStore.class);
    }
}
