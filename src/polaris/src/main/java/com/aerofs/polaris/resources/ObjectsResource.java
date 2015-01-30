package com.aerofs.polaris.resources;

import com.aerofs.auth.server.Roles;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@RolesAllowed(Roles.USER)
@Path("/objects")
@Singleton
public final class ObjectsResource {

    private final ResourceContext context;

    public ObjectsResource(@Context ResourceContext context) {
        this.context = context;
    }

    @Path("/{oid}")
    public ObjectResource operation() {
        return context.getResource(ObjectResource.class);
    }
}
