package com.aerofs.polaris.resources;

import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@Singleton
@Path("/objects")
public final class ObjectsResource {

    private final ResourceContext resourceContext;

    public ObjectsResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/{oid}")
    public ObjectResource operation(@PathParam("oid") String oid) {
        return resourceContext.getResource(ObjectResource.class);
    }
}
