package com.aerofs.polaris.resources;

import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.api.TransformType;
import com.aerofs.polaris.api.Update;
import com.aerofs.polaris.dao.Children;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.Metadata;
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

    @Path("/versions")
    public VersionsResource getVersions() {
        return resourceContext.getResource(VersionsResource.class);
    }

    @Path("/changes")
    public ChangesResource getChanges() {
        return resourceContext.getResource(ChangesResource.class);
    }

    // the following methods run within a single database transaction

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public LogicalObject update(final Update update) throws UpdateFailedException {
        LogicalObject updated = dbi.inTransaction(new TransactionCallback<LogicalObject>() {

            @Override
            public LogicalObject inTransaction(Handle conn, TransactionStatus status) throws Exception {
                switch (update.transformType) {
                    case INSERT_CHILD:
                        return insertChild(conn, update);
                    case REMOVE_CHILD:
                        return removeChild(conn, update);
                    case MAKE_CONTENT:
                        return makeContent(conn, update);
                    default:
                        throw new UnsupportedTransformType(oid, update.transformType);
                }
            }
        });

        if (updated != null) {
            return updated;
        } else {
            throw new UpdateFailedException(oid);
        }
    }

    @Nullable
    private LogicalObject insertChild(Handle conn, Update update) throws NotFoundException, AlreadyExistsException, NameConflictException, UpdateFailedException {
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
                throw new NotFoundException(oid);
            }
        }

        // check that it's something to which we can add a child
        ObjectType parentObjectType = objectTypes.get(oid);
        if (parentObjectType != ObjectType.ROOT && parentObjectType != ObjectType.FOLDER) {
            throw new UpdateFailedException(oid);
        }

        // can't insert a child that already exists
        if (logicalObjects.get(update.child) != null) {
            throw new AlreadyExistsException(oid, update.child);
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

    @Nullable
    private LogicalObject removeChild(Handle conn, Update update) throws NotFoundException, VersionMismatchException, UpdateFailedException {
        Preconditions.checkArgument(update.child != null);

        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);

        // FIXME (AG): this block of code is common
        // this object exists
        LogicalObject parentObject = logicalObjects.get(oid);
        if (parentObject == null) {
            if (Constants.isSharedFolder(oid)) {
                logicalObjects.add(oid, oid, 0);
                ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
                objectTypes.add(oid, ObjectType.ROOT);
                parentObject = new LogicalObject(oid, oid, INITIAL_OBJECT_VERSION);
            } else {
                throw new NotFoundException(oid);
            }
        }

        // the server version and local version of the parent match
        if (update.localVersion != parentObject.version) {
            throw new VersionMismatchException(oid, update.localVersion, parentObject.version);
        }

        // the child we're removing exists
        LogicalObject childObject = logicalObjects.get(update.child);
        if (childObject == null) {
            throw new NotFoundException(oid);
        }

        Children children = conn.attach(Children.class);

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(children.isChild(oid, update.child));

        // check that the child we're removing has no children itself
        if (children.countChildren(update.child) != 0) {
            throw new UpdateFailedException(update.child);
        }

        // at this point we've done all the input checks
        // we're ready to go ahead and actually start removing

        // add a row in the transforms table
        Transforms transforms = conn.attach(Transforms.class);
        long newParentVersion = parentObject.version + 1;
        transforms.add(parentObject.root, oid, TransformType.REMOVE_CHILD, newParentVersion, update.child, null);

        // update the version of the parent object
        logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // remove the child from the list of children
        children.remove(oid, update.child);

        // remove the child from the object table
        logicalObjects.remove(update.child);

        // return the latest version of the parent object
        return logicalObjects.get(oid);
    }

    private LogicalObject makeContent(Handle conn, Update update) throws NotFoundException, VersionMismatchException {
        Preconditions.checkArgument(update.contentHash != null);
        Preconditions.checkArgument(!update.contentHash.isEmpty());
        Preconditions.checkArgument(update.contentSize > 0);
        Preconditions.checkArgument(update.contentMtime > 0);

        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);

        // check that the object exists
        LogicalObject logicalObject = logicalObjects.get(oid);
        if (logicalObject == null) {
            throw new NotFoundException(oid);
        }

        // check that we're trying to add content for a file
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        ObjectType objectType = objectTypes.get(oid);
        Preconditions.checkArgument(objectType == ObjectType.FILE, "cannot add content for %s type", objectType);

        // check that we're at the right version
        if (update.localVersion != logicalObject.version) {
            throw new VersionMismatchException(oid, update.localVersion, logicalObject.version);
        }

        // this file exists with a matching version
        // at this point we can safely add content

        long newObjectVersion = logicalObject.version + 1;

        // add an entry in the transforms table
        Transforms transforms = conn.attach(Transforms.class);
        transforms.add(logicalObject.root, oid, TransformType.MAKE_CONTENT, newObjectVersion, null, null);

        // add a row to the content table
        Metadata metadata = conn.attach(Metadata.class);
        metadata.add(oid, newObjectVersion, update.contentHash, update.contentSize, update.contentMtime);

        // update the version for the object
        logicalObjects.update(logicalObject.root, oid, newObjectVersion);

        // return the latest version of the object
        return logicalObjects.get(oid);
    }
}
