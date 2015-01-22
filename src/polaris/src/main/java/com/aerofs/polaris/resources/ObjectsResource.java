package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.aero.Roles;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@RolesAllowed(Roles.CLIENT)
@Path("/objects")
@Singleton
public final class ObjectsResource {

    private final ResourceContext resourceContext;

    public ObjectsResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/{oid}")
    public ObjectResource operation() {
        return resourceContext.getResource(ObjectResource.class);
    }
}
