package com.aerofs.polaris.logical;

import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.api.TransformType;
import com.aerofs.polaris.api.Update;
import com.aerofs.polaris.dao.Children;
import com.aerofs.polaris.dao.Locations;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.ObjectProperties;
import com.aerofs.polaris.dao.ObjectTypes;
import com.aerofs.polaris.dao.Transforms;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class LogicalObjectStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogicalObjectStore.class);

    private final DBI dbi;

    public LogicalObjectStore(DBI dbi) {
        this.dbi = dbi;
    }

    // IMPORTANT: it is *crucial* that notifications go out in transform order

    /**
     * Use this method to run multiple transforms on the
     * logical object state in a single transaction. This
     * method is a very thin wrapper over the polaris
     * JDBI object.
     */
    public <ReturnType> ReturnType inTransaction(final TransactionCallback<ReturnType> callback) throws CallbackFailedException
    {
        return dbi.inTransaction(callback);
    }

    // FIXME (AG): have to mark and end transaction
    // FIXME (AG): clean up operations in here
    // FIXME (AG): deal with deleted objects somehow
    // FIXME (AG): figure out hardlinks

    //
    // high-level operations
    //

    public LogicalObject transform(Handle conn, String oid, Update update) throws UpdateFailedException, NotFoundException, NameConflictException, VersionConflictException {
        switch (update.transformType) {
            case INSERT_CHILD:
                return insertChild(conn, oid, update);
            case REMOVE_CHILD:
                return removeChild(conn, oid, update);
            case RENAME_CHILD:
                return renameChild(conn, oid, update);
            case UPDATE_CONTENT:
                return makeContent(conn, oid, update);
            default:
                throw new IllegalArgumentException("unsupported transform " + update.transformType);
        }
    }

    public LogicalObject insertChild(Handle conn, String oid, Update update) throws NotFoundException, NameConflictException, UpdateFailedException {
        Preconditions.checkArgument(update.child != null);
        Preconditions.checkArgument(update.childName != null);

        // dao objects
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        Transforms transforms = conn.attach(Transforms.class);
        Children children = conn.attach(Children.class);

        // get the parent
        LogicalObject parentObject = getOrCreateParent(logicalObjects, objectTypes, oid);
        if (parentObject.objectType != ObjectType.ROOT && parentObject.objectType != ObjectType.FOLDER) {
            throw new UpdateFailedException(oid, "is not a ROOT or FOLDER");
        }

        // check for name conflicts within the parent
        int matchingNamesCount = children.countChildrenWithName(oid, update.childName);
        if (matchingNamesCount != 0) {
            throw new NameConflictException(oid, update.childName);
        }

        LogicalObject childObject;

        // check if the caller is intending to create a new
        // object or simply create a hardlink to an existing object
        ObjectType childObjectType = objectTypes.get(update.child);
        if (childObjectType != null) {
            Preconditions.checkArgument(update.childObjectType == null || update.childObjectType.equals(childObjectType), "mismatched object type exp:%s act:%s", childObjectType, update.childObjectType);
            childObject = logicalObjects.get(update.child);
        } else {
            Preconditions.checkArgument(update.childObjectType != null);
            childObject = newObject(logicalObjects, objectTypes, oid, update.child, update.childObjectType);
        }

        // either unrooted or not, the child object should exist
        Preconditions.checkState(childObject != null, "child:%s", update.child);

        // attach the object to the requested parent
        attachChild(logicalObjects, transforms, children, parentObject, update.child, update.childName);

        // check if we have to root the object
        if (childObject.root.equals(Constants.NO_ROOT) && !parentObject.root.equals(Constants.NO_ROOT)) {
            logicalObjects.update(parentObject.root, childObject.oid, childObject.version);
        }

        LOGGER.info("insert {} into {}", update.child, oid);

        // return the latest version of the *parent* object
        return logicalObjects.get(oid);
    }

    public LogicalObject removeChild(Handle conn, String oid, Update update) throws NotFoundException, VersionConflictException, UpdateFailedException {
        Preconditions.checkArgument(update.child != null);

        // dao objects
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        Transforms transforms = conn.attach(Transforms.class);
        Children children = conn.attach(Children.class);

        // get the parent
        LogicalObject parentObject = getOrCreateParent(logicalObjects, objectTypes, oid);

        // the child we're removing exists
        LogicalObject childObject = logicalObjects.get(update.child);
        if (childObject == null) {
            throw new UpdateFailedException(oid, update.child + " does not exist");
        }

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(children.isChild(oid, update.child));

        // detach the child from its parent
        detachChild(logicalObjects, transforms, children, parentObject, update.child);

        // check if we should unroot the object
        if (!children.isChild(oid)) {
            logicalObjects.update(Constants.NO_ROOT, childObject.oid, childObject.version);
        }

        LOGGER.info("remove {} from {}", update.child, oid);

        // return the latest version of the *parent* object
        return logicalObjects.get(oid);
    }

    public LogicalObject renameChild(Handle conn, String oid, Update update) throws NotFoundException, VersionConflictException, UpdateFailedException, NameConflictException {
        Preconditions.checkArgument(update.child != null);
        Preconditions.checkArgument(update.childName != null);

        // dao objects
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        Transforms transforms = conn.attach(Transforms.class);
        Children children = conn.attach(Children.class);

        // get the parent
        LogicalObject parentObject = getOrCreateParent(logicalObjects, objectTypes, oid);

        // the child we're removing exists
        LogicalObject childObject = logicalObjects.get(update.child);
        if (childObject == null) {
            throw new UpdateFailedException(oid, update.child + " does not exist");
        }

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(children.isChild(oid, update.child));

        // get the current child name (informational)
        String currentChildName = children.getChildName(oid, update.child);

        // check for name conflicts with the new name
        int matchingNamesCount = children.countChildrenWithName(oid, update.childName);
        if (matchingNamesCount != 0) {
            throw new NameConflictException(oid, update.childName);
        }

        // rename the child to the new name within the same tree
        renameChild(logicalObjects, transforms, parentObject, update.child, update.childName);

        LOGGER.info("rename {} from {} to {} in {}", update.child, currentChildName, update.childName, oid);

        // return the latest version of the *parent* object
        return logicalObjects.get(oid);
    }

    public LogicalObject makeContent(Handle conn, String oid, Update update) throws NotFoundException, VersionConflictException {
        Preconditions.checkArgument(update.contentHash != null && !update.contentHash.isEmpty());
        Preconditions.checkArgument(update.contentSize > 0);
        Preconditions.checkArgument(update.contentMtime > 0);

        // dao objects
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        Transforms transforms = conn.attach(Transforms.class);
        ObjectProperties objectProperties = conn.attach(ObjectProperties.class);

        // check that the object exists
        LogicalObject fileObject = logicalObjects.get(oid);
        if (fileObject == null) {
            throw new NotFoundException(oid);
        }

        // check that we're trying to add content for a file
        ObjectType objectType = objectTypes.get(oid);
        Preconditions.checkArgument(objectType == ObjectType.FILE, "cannot add content for %s type", objectType);

        // check that we're at the right version
        if (update.localVersion != fileObject.version) {
            throw new VersionConflictException(oid, update.localVersion, fileObject.version);
        }

        // create an entry for a new version of the content
        newContent(logicalObjects, transforms, objectProperties, fileObject, update.contentHash, update.contentSize, update.contentMtime);

        // return the latest version of the object
        return logicalObjects.get(oid);
    }

    public List<Transform> getTransformsSince(Handle conn, String oid, long startTimestamp, long maxResultCount) {
        Preconditions.checkArgument(LogicalObjectStore.isSharedFolder(oid), "oid %s not an SID", oid);

        List<Transform> returned = Lists.newArrayList();

        Transforms transforms = conn.attach(Transforms.class);
        ResultIterator<Transform> iterator = transforms.getTransformsSince(startTimestamp, oid);
        try {
            while (iterator.hasNext() && returned.size() < maxResultCount) {
                returned.add(iterator.next());
            }
        } finally {
            iterator.close();
        }

        return returned;
    }

    // FIXME (AG): are both these operations noops if the location exists/doesn't exist?

    public void addLocation(Handle conn, String oid, long version, String did) throws NotFoundException {
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);

        // check that the object exists
        LogicalObject object = logicalObjects.get(oid);
        if (object == null) {
            throw new NotFoundException(oid);
        }

        // check that the version looks right
        checkVersionInRange(version, object);

        // check that the object is a file
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        ObjectType objectType = objectTypes.get(oid);
        Preconditions.checkArgument(objectType == ObjectType.FILE, "cannot add content for", objectType);

        // now, let's add the new location for the object
        Locations locations = conn.attach(Locations.class);
        locations.add(oid, version, did);
    }

    // FIXME (AG): deal with deleted objects
    public void removeLocation(Handle conn, String oid, long version, String did) throws NotFoundException {
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);

        // check that the object exists
        LogicalObject object = logicalObjects.get(oid);
        if (object == null) {
            throw new NotFoundException(oid);
        }

        // check that the version looks right
        checkVersionInRange(version, object);

        // check that the object is a file
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        ObjectType objectType = objectTypes.get(oid);
        Preconditions.checkArgument(objectType == ObjectType.FILE, "cannot add content for", objectType);

        // now, let's remove the existing location for the object
        Locations locations = conn.attach(Locations.class);
        locations.remove(oid, version, did);
    }

    // FIXME (AG): deal with deleted objects
    public List<String> getLocations(Handle conn, String oid, long version) throws NotFoundException {
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);

        // check that the object exists
        LogicalObject object = logicalObjects.get(oid);
        if (object == null) {
            throw new NotFoundException(oid);
        }
        checkVersionInRange(version, object);


        // check that the object is a file
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        ObjectType objectType = objectTypes.get(oid);
        Preconditions.checkArgument(objectType == ObjectType.FILE, "cannot add content for", objectType);

        List<String> existingLocations = Lists.newArrayListWithCapacity(10);

        // now, let's get the list of devices that have this content
        Locations locations = conn.attach(Locations.class);
        ResultIterator<String> iterator = locations.get(oid, version);
        try {
            while (iterator.hasNext()) {
                existingLocations.add(iterator.next());
            }
        } finally {
            iterator.close();
        }

        return existingLocations;
    }

    private void checkVersionInRange(long version, LogicalObject object) {
        // check that the version looks right
        Preconditions.checkArgument(version >= 0, "version %s less than 0", version);
        Preconditions.checkArgument(version <= object.version, "version %s exceeds upper bound of %s", version, object.version);
    }

    private LogicalObject getOrCreateParent(LogicalObjects logicalObjects, ObjectTypes objectTypes, String oid) throws NotFoundException {
        // check that the parent exists
        LogicalObject parentObject = logicalObjects.get(oid);

        // create it if it's a shared folder root
        if (parentObject == null) {
            if (LogicalObjectStore.isSharedFolder(oid)) {
                parentObject = newRoot(logicalObjects, objectTypes, oid);
            } else {
                throw new NotFoundException(oid);
            }
        }

        return parentObject;
    }

    //
    // primitive operations
    //
    // these methods do *not* check pre/post conditions
    //

    private LogicalObject newRoot(LogicalObjects logicalObjects, ObjectTypes objectTypes, String oid) {
        Preconditions.checkArgument(isSharedFolder(oid), "%s not shared folder id", oid);

        return newObject(logicalObjects, objectTypes, oid, oid, ObjectType.ROOT);
    }

    private LogicalObject newObject(LogicalObjects logicalObjects, ObjectTypes objectTypes, String root, String oid, ObjectType objectType) {
        // create the object at the initial version
        logicalObjects.add(root, oid, Constants.INITIAL_OBJECT_VERSION);

        // add a type-mapping for the object
        objectTypes.add(oid, objectType);

        // return the newly created object
        return logicalObjects.get(oid);
    }

    private void removeObject(LogicalObjects logicalObjects, String oid) {
        Preconditions.checkArgument(!isSharedFolder(oid));

        logicalObjects.remove(oid);
    }

    private void attachChild(LogicalObjects logicalObjects, Transforms transforms, Children children, LogicalObject parentObject, String child, String childName) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        transforms.add(parentObject.root, parentObject.oid, TransformType.INSERT_CHILD, newParentVersion, child, childName);

        // update the version of the parent
        logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // create an entry for the child
        children.add(parentObject.oid, child, childName);
    }

    private void detachChild(LogicalObjects logicalObjects, Transforms transforms, Children children, LogicalObject parentObject, String child) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        transforms.add(parentObject.root, parentObject.oid, TransformType.REMOVE_CHILD, newParentVersion, child, null);

        // update the version of the parent
        logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // remove the entry for the child
        children.remove(parentObject.oid, child);
    }

    private void renameChild(LogicalObjects logicalObjects, Transforms transforms, LogicalObject parentObject, String child, String childName) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        transforms.add(parentObject.root, parentObject.oid, TransformType.RENAME_CHILD, newParentVersion, child, childName);

        // update the version of the parent
        logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);
    }

    private void newContent(LogicalObjects logicalObjects, Transforms transforms, ObjectProperties objectProperties, LogicalObject fileObject, String hash, long size, long mtime) {
        long newVersion = fileObject.version + 1;

        // add an entry in the transforms table
        transforms.add(fileObject.root, fileObject.oid, TransformType.UPDATE_CONTENT, newVersion, null, null);

        // add a row to the content table
        objectProperties.add(fileObject.oid, newVersion, hash, size, mtime);

        // update the version for the object
        logicalObjects.update(fileObject.root, fileObject.oid, newVersion);
    }

    //
    // utility functions
    //

    private static boolean isSharedFolder(String oid) {
        return oid.startsWith("SF");
    }
}
