package com.aerofs.polaris.resources;

import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@Singleton
public final class VersionsResource {

    private final ResourceContext resourceContext;

    public VersionsResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/{version}")
    public final VersionResource get() {
        return resourceContext.getResource(VersionResource.class);
    }
}
