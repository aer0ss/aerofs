package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.AeroPrincipal;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@RolesAllowed(AeroPrincipal.CLIENT_ROLE)
@Singleton
public final class LocationsResource {

    private final ResourceContext resourceContext;

    public LocationsResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/{did}")
    public LocationResource operation() {
        return resourceContext.getResource(LocationResource.class);
    }
}
