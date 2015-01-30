package com.aerofs.polaris.resources;

import com.aerofs.auth.server.Roles;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.Path;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;

@RolesAllowed(Roles.USER)
@Singleton
public final class VersionsResource {

    private final ResourceContext context;

    public VersionsResource(@Context ResourceContext context) {
        this.context = context;
    }

    @Path("/{version}")
    public VersionResource get() {
        return context.getResource(VersionResource.class);
    }
}
