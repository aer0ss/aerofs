package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.UniqueID;
import com.aerofs.polaris.api.types.JobStatus;
import com.aerofs.polaris.logical.Migrator;

import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@RolesAllowed(Roles.USER)
@Path("/jobs")
@Singleton
public class JobsResource {
    private final Migrator migrator;

    public JobsResource(@Context Migrator migrator) {
        this.migrator = migrator;
    }

    @Path("/{jid}")
    @Produces(MediaType.APPLICATION_JSON)
    @GET
    public JobStatus.Response getTransforms(@Context AeroUserDevicePrincipal principal, @PathParam("jid") UniqueID jobID) {
        return this.migrator.getJobStatus(jobID).asResponse();
    }
}
