package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.aero.Roles;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@RolesAllowed(Roles.CLIENT)
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
