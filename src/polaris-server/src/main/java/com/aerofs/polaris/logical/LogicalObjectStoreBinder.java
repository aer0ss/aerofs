package com.aerofs.polaris.logical;

import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.ws.rs.ext.Provider;

@Provider
public final class LogicalObjectStoreBinder extends AbstractBinder {

    private final LogicalObjectStoreFactory logicalObjectStoreFactory;

    public LogicalObjectStoreBinder(LogicalObjectStore logicalObjectStore) {
        this.logicalObjectStoreFactory = new LogicalObjectStoreFactory(logicalObjectStore);
    }

    @Override
    protected void configure() {
        bindFactory(logicalObjectStoreFactory).to(LogicalObjectStore.class);
    }

    private static final class LogicalObjectStoreFactory implements Factory<LogicalObjectStore> {

        private final LogicalObjectStore logicalObjectStore;

        public LogicalObjectStoreFactory(LogicalObjectStore logicalObjectStore) {
            this.logicalObjectStore = logicalObjectStore;
        }

        @Override
        public LogicalObjectStore provide() {
            return logicalObjectStore;
        }

        @Override
        public void dispose(LogicalObjectStore instance) {
            // noop; once created, we never remove DBI instances
        }
    }
}
