package com.aerofs.polaris.logical;

import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.api.TransformType;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.dao.Children;
import com.aerofs.polaris.dao.Locations;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.ObjectProperties;
import com.aerofs.polaris.dao.ObjectTypes;
import com.aerofs.polaris.dao.Transforms;
import com.aerofs.polaris.ids.Identifiers;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.exceptions.CallbackFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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

    public List<LogicalObject> performOperation(Handle conn, String oid, Operation operation) throws NotFoundException, NameConflictException, VersionConflictException {
        switch (operation.type) {
            case INSERT_CHILD: {
                InsertChild insertChild = (InsertChild) operation;
                return ImmutableList.of(insertChild(conn, oid, insertChild.child, insertChild.childObjectType, insertChild.childName));
            }
            case MOVE_CHILD: {
                MoveChild moveChild = (MoveChild) operation;
                if (oid.equals(moveChild.newParent)) {
                    return ImmutableList.of(renameChild(conn, oid, moveChild.child, moveChild.newChildName));
                } else {
                    return ImmutableList.of(insertChild(conn, moveChild.newParent, moveChild.child, null, moveChild.newChildName), removeChild(conn, oid, moveChild.child));
                }
            }
            case REMOVE_CHILD: {
                RemoveChild removeChild = (RemoveChild) operation;
                return ImmutableList.of(removeChild(conn, oid, removeChild.child));
            }
            case UPDATE_CONTENT: {
                UpdateContent updateContent = (UpdateContent) operation;
                return ImmutableList.of(makeContent(conn, oid, updateContent.localVersion, updateContent.contentHash, updateContent.contentSize, updateContent.contentMTime));
            }
            default:
                throw new IllegalArgumentException("unsupported operation " + operation.type);
        }
    }

    public LogicalObject insertChild(Handle conn, String oid, String child, @Nullable ObjectType childObjectType, String childName) throws NotFoundException, NameConflictException {

        // FIXME (AG): NOP reinsert of child
        // FIXME (AG): do not allow insert of child under different tree

        // dao objects
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        Transforms transforms = conn.attach(Transforms.class);
        Children children = conn.attach(Children.class);

        // get the parent
        LogicalObject parentObject = getOrCreateParent(logicalObjects, objectTypes, oid);
        Preconditions.checkArgument(parentObject.objectType == ObjectType.ROOT || parentObject.objectType == ObjectType.FOLDER);

        // check for name conflicts within the parent
        int matchingNamesCount = children.countChildrenWithName(oid, childName);
        if (matchingNamesCount != 0) {
            LogicalObject conflictingChildObject = getConflictingChild(children, logicalObjects, childName);
            throw new NameConflictException(oid, childName, conflictingChildObject);
        }

        LogicalObject childObject;

        // check if the caller is intending to create a new
        // object or simply create a hardlink to an existing object
        ObjectType storedChildObjectType = objectTypes.get(child);
        if (storedChildObjectType != null) {
            Preconditions.checkArgument(childObjectType == null || childObjectType.equals(storedChildObjectType), "mismatched object type exp:%s act:%s", storedChildObjectType, childObjectType);
            childObject = logicalObjects.get(child);
        } else {
            Preconditions.checkNotNull(childObjectType);
            childObject = newObject(logicalObjects, objectTypes, parentObject.root, child, childObjectType);
        }

        // either unrooted or not, the child object should exist
        Preconditions.checkState(childObject != null, "child:%s", child);

        // attach the object to the requested parent
        attachChild(logicalObjects, transforms, children, parentObject, child, childName);

        // check if we have to root the object
        if (childObject.root.equals(Constants.NO_ROOT) && !parentObject.root.equals(Constants.NO_ROOT)) {
            logicalObjects.update(parentObject.root, childObject.oid, childObject.version);
        }

        LOGGER.info("insert {} into {}", child, oid);

        // return the latest version of the *parent* object
        return logicalObjects.get(oid);
    }

    public LogicalObject renameChild(Handle conn, String oid, String child, String newChildName) throws NotFoundException, VersionConflictException, NameConflictException {
        // dao objects
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        Transforms transforms = conn.attach(Transforms.class);
        Children children = conn.attach(Children.class);

        // get the parent
        LogicalObject parentObject = getOrCreateParent(logicalObjects, objectTypes, oid);

        // the child we're removing exists
        LogicalObject childObject = logicalObjects.get(child);
        if (childObject == null) {
            throw new NotFoundException(oid);
        }

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(children.isChild(oid, child));

        // get the current child name (informational)
        String currentChildName = children.getChildName(oid, child);

        // check for name conflicts with the new name
        int matchingNamesCount = children.countChildrenWithName(oid, newChildName);
        if (matchingNamesCount != 0) {
            LogicalObject conflictingChildObject = getConflictingChild(children, logicalObjects, newChildName);
            throw new NameConflictException(oid, newChildName, conflictingChildObject);
        }

        // rename the child to the new name within the same tree
        renameChild(logicalObjects, transforms, parentObject, child, newChildName);

        LOGGER.info("rename {} from {} to {} in {}", child, currentChildName, newChildName, oid);

        // return the latest version of the *parent* object
        return logicalObjects.get(oid);
    }

    private LogicalObject getConflictingChild(Children children, LogicalObjects logicalObjects, String childName) {
        String conflictingChild = children.getChildWithName(childName);
        Preconditions.checkNotNull(conflictingChild);
        return logicalObjects.get(conflictingChild);
    }

    public LogicalObject removeChild(Handle conn, String oid, String child) throws NotFoundException, VersionConflictException {
        // dao objects
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        Transforms transforms = conn.attach(Transforms.class);
        Children children = conn.attach(Children.class);

        // get the parent
        LogicalObject parentObject = getOrCreateParent(logicalObjects, objectTypes, oid);

        // the child we're removing exists
        LogicalObject childObject = logicalObjects.get(child);
        if (childObject == null) {
            throw new NotFoundException(oid);
        }

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(children.isChild(oid, child));

        // detach the child from its parent
        detachChild(logicalObjects, transforms, children, parentObject, child);

        // check if we should unroot the object
        if (!children.isChild(oid)) {
            logicalObjects.update(Constants.NO_ROOT, childObject.oid, childObject.version);
        }

        LOGGER.info("remove {} from {}", child, oid);

        // return the latest version of the *parent* object
        return logicalObjects.get(oid);
    }

    public LogicalObject makeContent(Handle conn, String oid, long localVersion, String contentHash, long contentSize, long contentMtime) throws NotFoundException, VersionConflictException {
        Preconditions.checkArgument(!contentHash.isEmpty());
        Preconditions.checkArgument(contentSize > 0);
        Preconditions.checkArgument(contentMtime > 0);

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
        if (localVersion != fileObject.version) {
            throw new VersionConflictException(oid, localVersion, fileObject.version);
        }

        // create an entry for a new version of the content
        newContent(logicalObjects, transforms, objectProperties, fileObject, contentHash, contentSize, contentMtime);

        // return the latest version of the object
        return logicalObjects.get(oid);
    }

    public List<Transform> getTransformsSince(Handle conn, String oid, long startTimestamp, long maxResultCount) {
        Preconditions.checkArgument(Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid), "oid %s not an SID", oid);

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
            if (Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid)) {
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
        Preconditions.checkArgument(Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid), "%s not shared folder id", oid);

        return newObject(logicalObjects, objectTypes, oid, oid, ObjectType.ROOT);
    }

    private LogicalObject newObject(LogicalObjects logicalObjects, ObjectTypes objectTypes, String root, String oid, ObjectType objectType) {
        Preconditions.checkArgument(Identifiers.isRootStore(root) || Identifiers.isSharedFolder(root), "invalid root %s", root);

        // create the object at the initial version
        logicalObjects.add(root, oid, Constants.INITIAL_OBJECT_VERSION);

        // add a type-mapping for the object
        objectTypes.add(oid, objectType);

        // return the newly created object
        return logicalObjects.get(oid);
    }

    private void removeObject(LogicalObjects logicalObjects, String oid) {
        Preconditions.checkArgument(!(Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid)));

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
}
