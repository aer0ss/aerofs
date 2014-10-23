package com.aerofs.polaris.logical;

import com.aerofs.ids.core.Identifiers;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.api.operation.InsertChild;
import com.aerofs.polaris.api.operation.MoveChild;
import com.aerofs.polaris.api.operation.Operation;
import com.aerofs.polaris.api.operation.RemoveChild;
import com.aerofs.polaris.api.operation.UpdateContent;
import com.aerofs.polaris.api.operation.Updated;
import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.api.types.Transform;
import com.aerofs.polaris.api.types.TransformType;
import com.aerofs.polaris.dao.Atomic;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.ResultIterator;
import org.skife.jdbi.v2.TransactionCallback;
import org.skife.jdbi.v2.TransactionStatus;
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

    public <ReturnType> ReturnType inTransaction(final Transactional<ReturnType> operation) {
        return dbi.inTransaction(new TransactionCallback<ReturnType>() {
            @Override
            public ReturnType inTransaction(Handle conn, TransactionStatus status) throws Exception {
                DAO dao = new DAO(conn);
                return operation.execute(dao);
            }
        });
    }

    public <ReturnType> ReturnType inTransaction(final Transactional<ReturnType> operation, final int isolationLevel) {
        return dbi.inTransaction(new TransactionCallback<ReturnType>() {
            @Override
            public ReturnType inTransaction(Handle conn, TransactionStatus status) throws Exception {
                conn.setTransactionIsolation(isolationLevel);
                DAO dao = new DAO(conn);
                return operation.execute(dao);
            }
        });
    }

    //
    // high-level operations
    //

    public List<Updated> performOperation(DAO dao, String origin, String oid, Operation operation) throws PolarisException {
        List<Updated> updated = Lists.newArrayListWithExpectedSize(2);

        switch (operation.type) {
            case INSERT_CHILD: {
                InsertChild insertChild = (InsertChild) operation;
                updated.add(insertChild(
                        dao,
                        origin,
                        oid,
                        insertChild.child,
                        insertChild.childObjectType,
                        insertChild.childName,
                        false,
                        null));
                break;
            }
            case MOVE_CHILD: {
                MoveChild moveChild = (MoveChild) operation;
                if (oid.equals(moveChild.newParent)) {
                    updated.add(renameChild(dao, origin, oid, moveChild.child, moveChild.newChildName));
                } else {
                    Atomic atomic = new Atomic(2);
                    updated.add(insertChild(
                            dao,
                            origin,
                            moveChild.newParent,
                            moveChild.child,
                            null,
                            moveChild.newChildName,
                            true,
                            atomic));
                    updated.add(removeChild(dao, origin, oid, moveChild.child, atomic));
                }
                break;
            }
            case REMOVE_CHILD: {
                RemoveChild removeChild = (RemoveChild) operation;
                updated.add(removeChild(dao, origin, oid, removeChild.child, null));
                break;
            }
            case UPDATE_CONTENT: {
                UpdateContent updateContent = (UpdateContent) operation;
                updated.add(makeContent(
                        dao,
                        origin,
                        oid,
                        updateContent.localVersion,
                        updateContent.hash,
                        updateContent.size,
                        updateContent.mtime));
                break;
            }
            default:
                throw new IllegalArgumentException("unsupported operation " + operation.type);
        }

        return updated;
    }

    private Updated insertChild(
            DAO dao,
            String origin,
            String oid,
            String child,
            @Nullable ObjectType childObjectType,
            String childName,
            boolean allowReinsert,
            @Nullable Atomic atomic) throws NotFoundException, NameConflictException, ParentConflictException {
        // check if the object was already inserted
        if (!allowReinsert) {
            String parent = dao.children.getParent(child);
            if (!(parent == null || parent.equals(Constants.NO_ROOT))) {
                LogicalObject conflictingParent = dao.logicalObjects.get(parent);
                Preconditions.checkState(conflictingParent != null, "no parent object for %s", parent);
                throw new ParentConflictException(child, oid, conflictingParent);
            }
        }

        // get the parent
        LogicalObject parentObject = getOrCreateParent(dao, oid);
        Preconditions.checkArgument(isFolder(parentObject.objectType), "cannot insert into %s", parentObject.objectType);

        // check for name conflicts within the parent
        checkForNameConflicts(dao, oid, childName);

        LogicalObject childObject;

        // check if the caller is intending to create a new
        // object or simply use an existing object
        ObjectType storedChildObjectType = dao.objectTypes.get(child);
        if (storedChildObjectType != null) {
            Preconditions.checkArgument(childObjectType == null || childObjectType.equals(storedChildObjectType), "mismatched object type exp:%s act:%s", storedChildObjectType, childObjectType);
            childObject = dao.logicalObjects.get(child);
        } else {
            Preconditions.checkArgument(childObjectType != null, "%s does not exist", child);
            childObject = newObject(dao, parentObject.root, child, childObjectType);
        }

        // by this point the child object should exist
        Preconditions.checkArgument(childObject != null, "%s does not exist", child);

        // attach the object to the requested parent
        long transformTimestamp = attachChild(dao, origin, parentObject, child, childName, atomic);

        LOGGER.info("insert {} into {}", child, oid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, oid));
    }

    private Updated renameChild(
            DAO dao,
            String origin,
            String oid,
            String child,
            String newChildName) throws NotFoundException, VersionConflictException, NameConflictException {
        // get the parent
        LogicalObject parentObject = getOrCreateParent(dao, oid);

        // the child we're removing exists
        LogicalObject childObject = getExistingObject(dao, child);
        if (childObject == null) {
            throw new NotFoundException(oid);
        }

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(dao.children.isChild(oid, child), "%s is not a child of %s", child, oid);

        // get the current child name (informational)
        String currentChildName = dao.children.getChildName(oid, child);

        // check for name conflicts with the new name
        checkForNameConflicts(dao, oid, newChildName);

        // rename the child to the new name within the same tree
        long transformTimestamp = renameChild(dao, origin, parentObject, child, newChildName);

        LOGGER.info("rename {} from {} to {} in {}", child, currentChildName, newChildName, oid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, oid));
    }

    private Updated removeChild(
            DAO dao,
            String origin,
            String oid,
            String child,
            @Nullable Atomic atomic) throws NotFoundException, VersionConflictException {
        Preconditions.checkArgument(!Identifiers.isRootStore(child), "cannot remove root store %s", child);

        // get the parent
        LogicalObject parentObject = getOrCreateParent(dao, oid);

        // the child we're removing exists
        LogicalObject childObject = dao.logicalObjects.get(child);
        if (childObject == null) {
            throw new NotFoundException(oid);
        }

        // the child we're removing is actually the child of this object
        String childName = dao.children.getChildName(oid, child);
        Preconditions.checkArgument(childName != null, "%s is not a child of %s", child, oid);

        // detach the child from its parent
        long transformTimestamp = detachChild(dao, origin, parentObject, child, atomic);

        // check if we should unroot the object
        if (dao.children.getActiveReferenceCount(child) == 0) {
            unrootObject(dao, childObject, childName);
        }

        LOGGER.info("remove {} from {}", child, oid);

        // return the latest version of the object
        return new Updated(transformTimestamp, getExistingObject(dao, oid));
    }

    private Updated makeContent(
            DAO dao,
            String origin,
            String oid,
            long localVersion,
            String contentHash,
            long contentSize,
            long contentMtime) throws NotFoundException, VersionConflictException {
        // check that the object exists
        LogicalObject fileObject = getExistingObject(dao, oid);

        // check that we're trying to add content for a file
        ObjectType objectType = dao.objectTypes.get(oid);
        Preconditions.checkArgument(isFile(objectType), "cannot add content for %s type", objectType);

        // check that we're at the right version
        if (localVersion != fileObject.version) {
            throw new VersionConflictException(oid, localVersion, fileObject.version);
        }

        // create an entry for a new version of the content
        long transformTimestamp = newContent(dao, origin, fileObject, contentHash, contentSize, contentMtime);

        // return the latest version of the object
        return new Updated(transformTimestamp, getExistingObject(dao, oid));
    }

    public void insertLocation(DAO dao, String oid, long version, String did) throws NotFoundException {
        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);

        // check that the version looks right
        checkVersionInRange(object, version);

        // check that the object is a file
        ObjectType objectType = dao.objectTypes.get(oid);
        Preconditions.checkArgument(isFile(objectType), "cannot add content for", objectType);

        // now, let's add the new location for the object
        dao.locations.add(oid, version, did);
    }

    public void removeLocation(DAO dao, String oid, long version, String did) throws NotFoundException {
        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);

        // check that the version looks right
        checkVersionInRange(object, version);

        // check that the object is a file
        ObjectType objectType = dao.objectTypes.get(oid);
        Preconditions.checkArgument(isFile(objectType), "cannot add content for", objectType);

        // now, let's remove the existing location for the object
        dao.locations.remove(oid, version, did);
    }

    //
    // helper methods
    //

    private boolean isFolder(ObjectType objectType) {
        return objectType == ObjectType.ROOT || objectType == ObjectType.FOLDER;
    }

    private boolean isFile(ObjectType objectType) {
        return objectType == ObjectType.FILE;
    }

    private void checkVersionInRange(LogicalObject object, long version) {
        Preconditions.checkArgument(version >= 0, "version %s less than 0", version);
        Preconditions.checkArgument(version <= object.version, "version %s exceeds upper bound of %s", version, object.version);
    }

    private LogicalObject getOrCreateParent(DAO dao, String oid) throws NotFoundException {
        // check that the parent exists
        LogicalObject parentObject = dao.logicalObjects.get(oid);

        // create it if it's a shared folder root
        if (parentObject == null) {
            if (Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid)) {
                parentObject = newRoot(dao, oid);
            } else {
                throw new NotFoundException(oid);
            }
        }

        return parentObject;
    }

    private LogicalObject getExistingObject(DAO dao, String oid) throws NotFoundException {
        LogicalObject fileObject = dao.logicalObjects.get(oid);

        if (fileObject == null) {
            throw new NotFoundException(oid);
        }

        return fileObject;
    }

    private void checkForNameConflicts(DAO dao, String parent, String childName) throws NameConflictException, NotFoundException {
        String conflictingChild = dao.children.getChildNamed(parent, childName);

        if (conflictingChild != null) {
            throw new NameConflictException(parent, childName, getExistingObject(dao, conflictingChild));
        }
    }

    //
    // primitive operations
    //
    // these methods do *not* check pre/post conditions
    //

    private LogicalObject newRoot(DAO dao, String oid) {
        verifyStore(oid);

        return newObject(dao, oid, oid, ObjectType.ROOT);
    }

    private LogicalObject newObject(DAO dao, String root, String oid, ObjectType objectType) {
        verifyStore(root);

        // create the object at the initial version
        dao.logicalObjects.add(root, oid, Constants.INITIAL_OBJECT_VERSION);

        // add a type-mapping for the object
        dao.objectTypes.add(oid, objectType);

        // return the newly created object
        return dao.logicalObjects.get(oid);
    }

    private long attachChild(DAO dao, String origin, LogicalObject parentObject, String child, String childName, @Nullable Atomic atomic) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        long logicalTimestamp = dao.transforms.add(
                origin,
                parentObject.root,
                parentObject.oid,
                TransformType.INSERT_CHILD,
                newParentVersion,
                child,
                childName,
                System.currentTimeMillis(),
                atomic);

        // update the version of the parent
        dao.logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // create an entry for the child
        dao.children.add(parentObject.oid, child, childName);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private long detachChild(DAO dao, String origin, LogicalObject parentObject, String child, @Nullable Atomic atomic) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        long logicalTimestamp = dao.transforms.add(
                origin,
                parentObject.root,
                parentObject.oid,
                TransformType.REMOVE_CHILD,
                newParentVersion,
                child,
                null,
                System.currentTimeMillis(),
                atomic);

        // update the version of the parent
        dao.logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // remove the entry for the child
        dao.children.remove(parentObject.oid, child);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private long renameChild(DAO dao, String origin, LogicalObject parentObject, String child, String childName) {
        long newParentVersion = parentObject.version + 1;

        // add the transform
        long logicalTimestamp = dao.transforms.add(
                origin,
                parentObject.root,
                parentObject.oid,
                TransformType.RENAME_CHILD,
                newParentVersion,
                child,
                childName,
                System.currentTimeMillis(),
                null);

        // update the version of the parent
        dao.logicalObjects.update(parentObject.root, parentObject.oid, newParentVersion);

        // update the child entry in the children table
        dao.children.update(parentObject.oid, child, childName);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private long newContent(DAO dao, String origin, LogicalObject fileObject, String hash, long size, long mtime) {
        long newVersion = fileObject.version + 1;

        // add an entry in the transforms table
        long logicalTimestamp = dao.transforms.add(
                origin,
                fileObject.root,
                fileObject.oid,
                TransformType.UPDATE_CONTENT,
                newVersion,
                null,
                null,
                System.currentTimeMillis(),
                null);

        // add a row to the content table
        dao.objectProperties.add(fileObject.oid, newVersion, hash, size, mtime);

        // update the version for the object
        dao.logicalObjects.update(fileObject.root, fileObject.oid, newVersion);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private void unrootObject(DAO dao, LogicalObject logicalObject, String name) {
        dao.logicalObjects.update(Constants.NO_ROOT, logicalObject.oid, logicalObject.version);
        dao.children.add(Constants.NO_ROOT, logicalObject.oid, name);
    }

    private static void verifyStore(String oid) {
        Preconditions.checkArgument(Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid), "%s not an sid", oid);
    }

    //
    // queries
    //

    public int getTransformCount(DAO dao, String oid) {
        verifyStore(oid);

        return dao.transforms.getTransformCount(oid);
    }

    public List<Transform> getTransformsSince(DAO dao, String oid, long startTimestamp, long maxResultCount) {
        verifyStore(oid);

        List<Transform> returned = Lists.newArrayList();
        ResultIterator<Transform> iterator = dao.transforms.getTransformsSince(startTimestamp, oid);
        try {
            while (iterator.hasNext() && returned.size() < maxResultCount) {
                returned.add(iterator.next());
            }
        } finally {
            iterator.close();
        }

        return returned;
    }

    public List<String> getLocations(DAO dao, String oid, long version) throws NotFoundException {
        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);
        checkVersionInRange(object, version);

        // check that the object is a file
        ObjectType objectType = dao.objectTypes.get(oid);
        Preconditions.checkArgument(isFile(objectType), "cannot add content for %s", objectType);

        List<String> existingLocations = Lists.newArrayListWithCapacity(10);

        // now, let's get the list of devices that have this content
        ResultIterator<String> iterator = dao.locations.get(oid, version);
        try {
            while (iterator.hasNext()) {
                existingLocations.add(iterator.next());
            }
        } finally {
            iterator.close();
        }

        return existingLocations;
    }
}
