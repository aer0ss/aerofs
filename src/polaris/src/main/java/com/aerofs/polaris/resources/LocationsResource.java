package com.aerofs.polaris.resources;

import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.dao.Locations;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.ObjectTypes;
import com.google.common.base.Preconditions;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;

public final class LocationsResource {

    private final DBI dbi;
    private final String oid;
    private final long version;

    public LocationsResource(@Context DBI dbi, @PathParam("oid") String oid, @PathParam("version") long version) {
        this.dbi = dbi;
        this.oid = oid;
        this.version = version;
    }

    @POST
    @Path("/{did}")
    public void markContentAtLocation(@PathParam("did") final String did) {
        dbi.inTransaction(new TransactionCallback<Void>() {

            @Override
            public Void inTransaction(Handle conn, TransactionStatus status) throws Exception {
                LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);

                // check that the object exists
                LogicalObject object = logicalObjects.get(oid);
                if (object == null) {
                    throw new NotFoundException(oid);
                }

                // check that the version looks right
                Preconditions.checkArgument(version >= 0, "version %s less than 0", version);
                Preconditions.checkArgument(version <= object.version, "version %s exceeds upper bound of %s", version, object.version);

                // check that the object is a file
                ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
                ObjectType objectType = objectTypes.get(oid);
                Preconditions.checkArgument(objectType == ObjectType.FILE, "cannot add content for", objectType);

                // now, let's add the new location for the object
                Locations locations = conn.attach(Locations.class);
                locations.add(oid, version, did);
                return null;
            }
        });
    }
}
