package com.aerofs.polaris.resources;

import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

public final class VersionResource {

    private final ResourceContext resourceContext;

    public VersionResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/locations")
    public LocationsResource devices() {
        return resourceContext.getResource(LocationsResource.class);
    }
}
