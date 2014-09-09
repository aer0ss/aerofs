package com.aerofs.polaris.resources;

import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.api.TransformType;
import com.aerofs.polaris.api.Update;
import com.aerofs.polaris.dao.Children;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.ObjectTypes;
import com.aerofs.polaris.dao.Transforms;
import com.google.common.base.Preconditions;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;

import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

public final class ObjectResource {

    private static final long INITIAL_OBJECT_VERSION = 0;

    private final DBI dbi;
    private final ResourceContext resourceContext;
    private final String oid;

    public ObjectResource(@Context DBI dbi, @Context ResourceContext resourceContext, @PathParam("oid") String oid) {
        this.dbi = dbi;
        this.resourceContext = resourceContext;
        this.oid = oid;
    }

//    @Path("/versions")
//    public VersionsResource getVersions() {
//        return new VersionsResource(dbi, oid);
//    }
//
    @Path("/changes")
    public ChangesResource getChanges() {
        return resourceContext.getResource(ChangesResource.class);
    }

    // the following methods run within a single database transaction

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LogicalObject update(final Update update) throws ObjectUpdateFailedException {
        LogicalObject updated = dbi.inTransaction(new TransactionCallback<LogicalObject>() {

            @Override
            public LogicalObject inTransaction(Handle conn, TransactionStatus status) throws Exception {
                switch (update.transformType) {
                    case INSERT_CHILD:
                        return insertChild(conn, update);
                    default:
                        throw new UnsupportedTransformType(oid, update.transformType);
                }
            }
        });

        if (updated != null) {
            return updated;
        } else {
            throw new ObjectUpdateFailedException(oid);
        }
    }

    @Nullable
    private LogicalObject insertChild(Handle conn, Update update) throws ObjectNotFoundException, ObjectAlreadyExistsException, NameConflictException, ObjectUpdateFailedException {
        Preconditions.checkArgument(update.child != null);
        Preconditions.checkArgument(update.childName != null);
        Preconditions.checkArgument(update.childObjectType != null);

        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);

        // check that the parent exists
        LogicalObject parentObject = logicalObjects.get(oid);

        if (parentObject == null) {
            if (Constants.isSharedFolder(oid)) {
                logicalObjects.add(oid, oid, 0);
                objectTypes.add(oid, ObjectType.ROOT);
                parentObject = new LogicalObject(oid, oid, INITIAL_OBJECT_VERSION);
            } else {
                throw new ObjectNotFoundException(oid);
            }
        }

        // check that it's something to which we can add a child
        ObjectType parentObjectType = objectTypes.get(oid);
        if (parentObjectType != ObjectType.ROOT && parentObjectType != ObjectType.FOLDER) {
            throw new ObjectUpdateFailedException(oid);
        }

        // can't insert a child that already exists
        if (logicalObjects.get(update.child) != null) {
            throw new ObjectAlreadyExistsException(oid, update.child);
        }

        Children children = conn.attach(Children.class);

        // check for name conflicts
        int matchingNamesCount = children.countChildrenWithName(oid, update.childName);
        if (matchingNamesCount != 0) {
            throw new NameConflictException(oid, update.childName);
        }

        // I don't actually care about the version of the parent
        // as long as there isn't a name conflict I can proceed

        // object doesn't exist and a name conflict doesn't exist
        // we can add the object

        long newParentVersion = parentObject.version + 1;

        // first, add the transform
        Transforms transforms = conn.attach(Transforms.class);
        transforms.add(parentObject.root, parentObject.oid, TransformType.INSERT_CHILD, newParentVersion, update.child, update.childName);

        // update the version of the parent
        logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // create an entry for the child
        children.add(oid, update.child, update.childName);

        // set the child object type
        objectTypes.add(update.child, update.childObjectType);

        // finally, add an entry into the objects table for the child
        logicalObjects.add(oid, update.child, INITIAL_OBJECT_VERSION);

        // return the child object
        return logicalObjects.get(update.child);
    }
}
