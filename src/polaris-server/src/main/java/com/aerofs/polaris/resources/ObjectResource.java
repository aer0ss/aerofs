package com.aerofs.polaris.resources;

import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.Update;
import com.aerofs.polaris.ids.Identifier;
import com.aerofs.polaris.logical.LogicalObjectStore;
import com.aerofs.polaris.logical.UpdateFailedException;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Singleton
public final class ObjectResource {

    private final LogicalObjectStore logicalObjectStore;
    private final ResourceContext resourceContext;

    public ObjectResource(@Context LogicalObjectStore logicalObjectStore, @Context ResourceContext resourceContext) {
        this.logicalObjectStore = logicalObjectStore;
        this.resourceContext = resourceContext;
    }

    @Path("/versions")
    public VersionsResource getVersions() {
        return resourceContext.getResource(VersionsResource.class);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LogicalObject update(@Identifier @PathParam("oid") final String oid, final Update update) throws UpdateFailedException {
        return logicalObjectStore.inTransaction(new TransactionCallback<LogicalObject>() {

            @Override
            public LogicalObject inTransaction(Handle conn, TransactionStatus status) throws Exception {
                return logicalObjectStore.transform(conn, oid, update);
            }
        });
    }
}
