package com.aerofs.polaris.sp;

import com.aerofs.polaris.acl.AccessManager;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import javax.ws.rs.ext.Provider;

@Provider
public final class SPAccessManagerBinder extends AbstractBinder {

    private final SPAccessManager accessManager;

    public SPAccessManagerBinder() {
        this.accessManager = new SPAccessManager();
    }

    @Override
    protected void configure() {
        bind(accessManager).to(AccessManager.class);
    }
}
