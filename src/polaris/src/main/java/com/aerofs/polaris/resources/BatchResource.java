package com.aerofs.polaris.resources;

import com.aerofs.auth.server.Roles;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@RolesAllowed(Roles.USER)
@Path("/batch")
@Singleton
public final class BatchResource {

    private final ResourceContext context;

    public BatchResource(@Context ResourceContext context) {
        this.context = context;
    }

    @Path("/transforms")
    public TransformBatchResource performObjectTransforms() {
        return context.getResource(TransformBatchResource.class);
    }
}
