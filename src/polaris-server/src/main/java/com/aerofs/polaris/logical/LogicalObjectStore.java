package com.aerofs.polaris.logical;

import com.aerofs.polaris.Constants;
import com.aerofs.polaris.api.LogicalObject;
import com.aerofs.polaris.api.ObjectType;
import com.aerofs.polaris.api.Transform;
import com.aerofs.polaris.api.TransformType;
import com.aerofs.polaris.api.Updated;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.dao.Atomic;
import com.aerofs.polaris.dao.Children;
import com.aerofs.polaris.dao.Locations;
import com.aerofs.polaris.dao.LogicalObjects;
import com.aerofs.polaris.dao.ObjectProperties;
import com.aerofs.polaris.dao.ObjectTypes;
import com.aerofs.polaris.dao.Transforms;
import com.aerofs.polaris.ids.Identifiers;
import com.google.common.base.Preconditions;
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

// FIXME (AG): this is such a shitty piece of code; methods have a crap-ton of parameters, the code looks ugly...
// FIXME (AG): consider making an instance for each resource object (with injected principal, resource, objects, etc.)
public final class LogicalObjectStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogicalObjectStore.class);

    private final DBI dbi;

    public LogicalObjectStore(DBI dbi) {
        this.dbi = dbi;
    }

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

    //
    // high-level operations
    //

    public int getTransformCount(Handle conn, String oid) {
        verifyStore(oid);

        Transforms transforms = conn.attach(Transforms.class);
        return transforms.getTransformCount(oid);
    }

    public List<Transform> getTransformsSince(Handle conn, String oid, long startTimestamp, long maxResultCount) {
        verifyStore(oid);

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
        Preconditions.checkArgument(objectType == ObjectType.FILE, "cannot add content for %s", objectType);

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

    public List<Updated> performOperation(Handle conn, String originator, String oid, Operation operation) throws NotFoundException, NameConflictException, VersionConflictException, ParentConflictException {
        List<Updated> updated = Lists.newArrayListWithExpectedSize(2);

        switch (operation.type) {
            case INSERT_CHILD:
                InsertChild insertChild = (InsertChild) operation;
                updated.add(insertChild(conn, originator, oid, insertChild.child, insertChild.childObjectType, insertChild.childName, false, null));
                break;
            case MOVE_CHILD:
                MoveChild moveChild = (MoveChild) operation;
                if (oid.equals(moveChild.newParent)) {
                    updated.add(renameChild(conn, originator, oid, moveChild.child, moveChild.newChildName));
                } else {
                    Atomic atomic = new Atomic(2);
                    updated.add(insertChild(conn, originator, moveChild.newParent, moveChild.child, null, moveChild.newChildName, true, atomic));
                    updated.add(removeChild(conn, originator, oid, moveChild.child, atomic));
                }
                break;
            case REMOVE_CHILD:
                RemoveChild removeChild = (RemoveChild) operation;
                updated.add(removeChild(conn, originator, oid, removeChild.child, null));
                break;
            case UPDATE_CONTENT:
                UpdateContent updateContent = (UpdateContent) operation;
                updated.add(makeContent(conn, originator, oid, updateContent.localVersion, updateContent.hash, updateContent.size, updateContent.mtime));
                break;
            default:
                throw new IllegalArgumentException("unsupported operation " + operation.type);
        }

        return updated;
    }

    // FIXME (AG): NOP reinsert of child
    // FIXME (AG): do not allow insert of child under different tree

    private Updated insertChild(Handle conn, String originator, String oid, String child, @Nullable ObjectType childObjectType, String childName, boolean allowReinsert, @Nullable Atomic atomic) throws NotFoundException, NameConflictException, ParentConflictException {
        // dao objects
        LogicalObjects logicalObjects = conn.attach(LogicalObjects.class);
        ObjectTypes objectTypes = conn.attach(ObjectTypes.class);
        Transforms transforms = conn.attach(Transforms.class);
        Children children = conn.attach(Children.class);

        // check if the object was already inserted
        if (!allowReinsert) {
            String parent = children.getParent(child);
            if (!(parent == null || parent.equals(Constants.NO_ROOT))) {
                LogicalObject conflictingParent = logicalObjects.get(parent);
                Preconditions.checkState(conflictingParent != null, "no parent object for %s", parent);
                throw new ParentConflictException(child, oid, conflictingParent);
            }
        }

        // get the parent
        LogicalObject parentObject = getOrCreateParent(logicalObjects, objectTypes, oid);
        Preconditions.checkArgument(parentObject.objectType == ObjectType.ROOT || parentObject.objectType == ObjectType.FOLDER, "cannot insert child in %s", parentObject.objectType);

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
            Preconditions.checkArgument(childObjectType != null, "%s does not exist", child);
            childObject = newObject(logicalObjects, objectTypes, parentObject.root, child, childObjectType);
        }

        // either unrooted or not, the child object should exist
        Preconditions.checkArgument(childObject != null, "%s does not exist", child);

        // attach the object to the requested parent
        long transformTimestamp = attachChild(logicalObjects, transforms, children, originator, parentObject, child, childName, atomic);

        // check if we have to root the object
        if (childObject.root.equals(Constants.NO_ROOT) && !parentObject.root.equals(Constants.NO_ROOT)) {
            logicalObjects.update(parentObject.root, childObject.oid, childObject.version);
        }

        LOGGER.info("insert {} into {}", child, oid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, logicalObjects.get(oid));
    }

    private Updated renameChild(Handle conn, String originator, String oid, String child, String newChildName) throws NotFoundException, VersionConflictException, NameConflictException {
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
        long transformTimestamp = renameChild(logicalObjects, transforms, children, originator, parentObject, child, newChildName);

        LOGGER.info("rename {} from {} to {} in {}", child, currentChildName, newChildName, oid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, logicalObjects.get(oid));
    }

    private LogicalObject getConflictingChild(Children children, LogicalObjects logicalObjects, String childName) {
        String conflictingChild = children.getChildWithName(childName);
        Preconditions.checkArgument(conflictingChild != null, "no child named %s", childName);
        return logicalObjects.get(conflictingChild);
    }

    private Updated removeChild(Handle conn, String originator, String oid, String child, @Nullable Atomic atomic) throws NotFoundException, VersionConflictException {
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
        long transformTimestamp = detachChild(logicalObjects, transforms, children, originator, parentObject, child, atomic);

        // check if we should unroot the object
        if (!children.isChild(oid)) {
            logicalObjects.update(Constants.NO_ROOT, childObject.oid, childObject.version);
        }

        LOGGER.info("remove {} from {}", child, oid);

        // return the latest version of the object
        return new Updated(transformTimestamp, logicalObjects.get(oid));
    }

    private Updated makeContent(Handle conn, String originator, String oid, long localVersion, String contentHash, long contentSize, long contentMtime) throws NotFoundException, VersionConflictException {
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
        long transformTimestamp = newContent(logicalObjects, transforms, objectProperties, originator, fileObject, contentHash, contentSize, contentMtime);

        // return the latest version of the object
        return new Updated(transformTimestamp, logicalObjects.get(oid));
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
        verifyStore(oid);

        return newObject(logicalObjects, objectTypes, oid, oid, ObjectType.ROOT);
    }

    private LogicalObject newObject(LogicalObjects logicalObjects, ObjectTypes objectTypes, String root, String oid, ObjectType objectType) {
        verifyStore(root);

        // create the object at the initial version
        logicalObjects.add(root, oid, Constants.INITIAL_OBJECT_VERSION);

        // add a type-mapping for the object
        objectTypes.add(oid, objectType);

        // return the newly created object
        return logicalObjects.get(oid);
    }

    private long attachChild(LogicalObjects logicalObjects, Transforms transforms, Children children, String originator, LogicalObject parentObject, String child, String childName, @Nullable Atomic atomic) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        long logicalTimestamp = transforms.add(originator, parentObject.root, parentObject.oid, TransformType.INSERT_CHILD, newParentVersion, child, childName, System.currentTimeMillis(), atomic);

        // update the version of the parent
        logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // create an entry for the child
        children.add(parentObject.oid, child, childName);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private long detachChild(LogicalObjects logicalObjects, Transforms transforms, Children children, String originator, LogicalObject parentObject, String child, @Nullable Atomic atomic) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        long logicalTimestamp = transforms.add(originator, parentObject.root, parentObject.oid, TransformType.REMOVE_CHILD, newParentVersion, child, null, System.currentTimeMillis(), atomic);

        // update the version of the parent
        logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // remove the entry for the child
        children.remove(parentObject.oid, child);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private long renameChild(LogicalObjects logicalObjects, Transforms transforms, Children children, String originator, LogicalObject parentObject, String child, String childName) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        long logicalTimestamp = transforms.add(originator, parentObject.root, parentObject.oid, TransformType.RENAME_CHILD, newParentVersion, child, childName, System.currentTimeMillis(), null);

        // update the version of the parent
        logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // update the child entry in the children table
        children.update(parentObject.oid, child, childName);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private long newContent(LogicalObjects logicalObjects, Transforms transforms, ObjectProperties objectProperties, String originator, LogicalObject fileObject, String hash, long size, long mtime) {
        long newVersion = fileObject.version + 1;

        // add an entry in the transforms table
        long logicalTimestamp = transforms.add(originator, fileObject.root, fileObject.oid, TransformType.UPDATE_CONTENT, newVersion, null, null, System.currentTimeMillis(), null);

        // add a row to the content table
        objectProperties.add(fileObject.oid, newVersion, hash, size, mtime);

        // update the version for the object
        logicalObjects.update(fileObject.root, fileObject.oid, newVersion);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static void verifyStore(String oid) {
        Preconditions.checkArgument(Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid), "%s not an sid", oid);
    }
}
