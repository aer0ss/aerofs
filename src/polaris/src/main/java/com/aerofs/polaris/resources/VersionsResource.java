package com.aerofs.polaris.resources;

import com.aerofs.baseline.auth.aero.Roles;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@RolesAllowed(Roles.USER)
@Singleton
public final class VersionsResource {

    private final ResourceContext resourceContext;

    public VersionsResource(@Context ResourceContext resourceContext) {
        this.resourceContext = resourceContext;
    }

    @Path("/{version}")
    public final VersionResource get() {
        return resourceContext.getResource(VersionResource.class);
    }
}
