package com.aerofs.polaris.resources;

import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@Singleton
public final class VersionResource {

    private final ResourceContext resourceContext;

    public VersionResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/locations")
    public LocationsResource locations() {
        return resourceContext.getResource(LocationsResource.class);
    }
}
