package com.aerofs.polaris.sp;

import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.logical.LogicalObjectStore;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.ws.rs.ext.Provider;

@Provider
public final class SPAccessManagerBinder extends AbstractBinder {

    private final SPAccessManagerFactory accessManagerFactory;

    public SPAccessManagerBinder() {
        this.accessManagerFactory = new SPAccessManagerFactory(new SPAccessManager());
    }

    @Override
    protected void configure() {
        bindFactory(accessManagerFactory).to(AccessManager.class);
    }

    private static final class SPAccessManagerFactory implements Factory<AccessManager> {

        private final SPAccessManager accessManager;

        public SPAccessManagerFactory(SPAccessManager accessManager) {
            this.accessManager = accessManager;
        }

        @Override
        public AccessManager provide() {
            return accessManager;
        }

        @Override
        public void dispose(AccessManager instance) {
            // noop; once created, we never remove this SPAccessManager instance
        }
    }
}
