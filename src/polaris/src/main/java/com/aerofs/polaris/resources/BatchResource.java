package com.aerofs.polaris.resources;

import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@Path("/batch")
@Singleton
public final class BatchResource {

    private final ResourceContext resourceContext;

    public BatchResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/transforms")
    public TransformBatchResource performObjectTransforms() {
        return resourceContext.getResource(TransformBatchResource.class);
    }

    @Path("/locations")
    public LocationBatchResource performLocationUpdates() {
        return resourceContext.getResource(LocationBatchResource.class);
    }
}
