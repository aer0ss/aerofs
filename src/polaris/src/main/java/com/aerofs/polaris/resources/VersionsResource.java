package com.aerofs.polaris.resources;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

public final class VersionsResource {

    private final ResourceContext resourceContext;

    public VersionsResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/{version}")
    public final VersionResource get(@PathParam("version") long version) {
        return resourceContext.getResource(VersionResource.class);
    }
}
