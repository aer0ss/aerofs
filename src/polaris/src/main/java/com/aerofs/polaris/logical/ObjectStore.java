package com.aerofs.polaris.logical;

import com.aerofs.ids.core.Identifiers;
import com.aerofs.ids.validation.Identifier;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.batch.location.LocationUpdateType;
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
import org.skife.jdbi.v2.ResultIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

// FIXME (AG): this is such a shitty piece of code; methods have a crap-ton of parameters, the code looks ugly...

/**
 * Interface through which components interact with the logical-object database.
 * <br>
 * This implementation can be used to:
 * <ul>
 *     <li>Insert, remove or move a logical object within a shared folder.</li>
 *     <li>Update content for a logical object.</li>
 *     <li>Insert or remove devices at which a versioned logical object is available.</li>
 *     <li>Get a list of transforms on a shared folder.</li>
 * </ul>
 * The methods in this class are re-entrant.
 */
@ThreadSafe
@Singleton
public final class ObjectStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStore.class);

    private final AccessManager accessManager;
    private final DBI dbi;

    /**
     * Constructor.
     *
     * @param accessManager implementation of {@code AccessManager} used to check if a user can access a logical object
     * @param dbi database wrapper used to read/update the logical object database
     */
    @Inject
    public ObjectStore(AccessManager accessManager, DBI dbi) {
        this.accessManager = accessManager;
        this.dbi = dbi;
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // transaction wrappers
    //
    //------------------------------------------------------------------------------------------------------------------

    public <ReturnType> ReturnType inTransaction(final StoreTransaction<ReturnType> operation) {
        return dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            return operation.execute(dao);
        });
    }

    public <ReturnType> ReturnType inTransaction(final StoreTransaction<ReturnType> operation, final int isolationLevel) {
        return dbi.inTransaction((conn, status) -> {
            conn.setTransactionIsolation(isolationLevel);
            DAO dao = new DAO(conn);
            return operation.execute(dao);
        });
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // access control
    //
    //------------------------------------------------------------------------------------------------------------------

    /**
     * Check if an object can be accessed by a user.
     *
     * @param dao database wrapper instance
     * @param user user id from which permissions should be checked
     * @param oid object the user wants to access
     * @param requested permissions the user wants on the object
     * @throws NotFoundException if the object does not exist
     * @throws AccessException if the user cannot access the object
     */
    private void checkAccess(DAO dao, String user, String oid, Access... requested) throws NotFoundException, AccessException {
        Preconditions.checkArgument(requested.length > 0, "at least one Access type required");

        String rootOid;

        if (Identifiers.isSharedFolder(oid) || Identifiers.isRootStore(oid)) {
            rootOid = oid;
        } else {
            LogicalObject object = dao.objects.get(oid);
            if (object == null) {
                throw new NotFoundException(oid);
            }

            rootOid = object.getRoot();
            Preconditions.checkState(rootOid != null, "no root for %s", oid);
        }

        accessManager.checkAccess(user, rootOid, requested);
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // logical object transformations
    //
    //------------------------------------------------------------------------------------------------------------------

    public int getTransformCount(DAO dao, String user, String oid) throws PolarisException {
        verifyStore(oid);

        checkAccess(dao, user, oid, Access.READ);

        return dao.transforms.getTransformCount(oid);
    }

    public List<Transform> getTransforms(DAO dao, String user, String oid, long startTimestamp, long maxReturnedResultCount) throws PolarisException {
        verifyStore(oid);

        checkAccess(dao, user, oid, Access.READ);

        List<Transform> returned = Lists.newArrayList();

        try (ResultIterator<Transform> iterator = dao.transforms.getTransformsSince(startTimestamp, oid)) {
            while (iterator.hasNext() && returned.size() < maxReturnedResultCount) {
                returned.add(iterator.next());
            }
        }

        return returned;
    }

    /**
     * Perform a high-level transformation on a logical object. This transformation can be an:
     * <ul>
     *     <li>Insertion</li>
     *     <li>Removal</li>
     *     <li>Move</li>
     *     <li>Content modification</li>
     * </ul>
     * Note that a high-level transformation may result in multiple primitive transformations.
     *
     * @param dao database interface used to realize the transformations
     * @param user user id of the user making the change to the object
     * @param device device that submitted the change
     * @param oid object being transformed
     * @param operation high-level transformation (one of the types listed above, along with their relevant parameters)
     * @return list of primitive transformations that resulted from this operation
     * @throws PolarisException if the requested operation could not be performed
     */
    public List<Updated> performTransform(DAO dao, String user, String device, String oid, Operation operation) throws PolarisException {
        Preconditions.checkNotNull(accessManager);

        checkAccess(dao, user, oid, Access.READ, Access.WRITE);

        List<Updated> updated = Lists.newArrayListWithExpectedSize(2);

        switch (operation.getType()) {
            case INSERT_CHILD: {
                InsertChild ic = (InsertChild) operation;
                updated.add(insertChild(dao, device, oid, ic.getChild(), ic.getChildObjectType(), ic.getChildNameBytes(), false, null));
                break;
            }
            case MOVE_CHILD: {
                MoveChild mc = (MoveChild) operation;
                if (oid.equals(mc.getNewParent())) {
                    updated.add(renameChild(dao, device, oid, mc.getChild(), mc.getNewChildNameBytes()));
                } else {
                    Atomic atomic = new Atomic(2);
                    updated.add(insertChild(dao, device, mc.getNewParent(), mc.getChild(), null, mc.getNewChildNameBytes(), true, atomic));
                    updated.add(removeChild(dao, device, oid, mc.getChild(), atomic));
                }
                break;
            }
            case REMOVE_CHILD: {
                RemoveChild rc = (RemoveChild) operation;
                updated.add(removeChild(dao, device, oid, rc.getChild(), null));
                break;
            }
            case UPDATE_CONTENT: {
                UpdateContent uc = (UpdateContent) operation;
                updated.add(makeContent(dao, device, oid, uc.getLocalVersion(), uc.getHash(), uc.getSize(), uc.getMtime()));
                break;
            }
            default:
                throw new IllegalArgumentException("unsupported operation " + operation.getType());
        }

        return updated;
    }

    private static Updated insertChild(DAO dao, String device, String parentOid, String childOid, @Nullable ObjectType childObjectType, byte[] childName, boolean allowReinsert, @Nullable Atomic atomic) throws PolarisException {
        // check if the object was already inserted
        if (!allowReinsert) {
            String currentParentOid = dao.children.getParent(childOid);

            if (!(currentParentOid == null || currentParentOid.equals(Constants.NO_ROOT))) {
                LogicalObject conflictingParent = dao.objects.get(currentParentOid);
                Preconditions.checkState(conflictingParent != null, "no parent object for %s", currentParentOid);
                throw new ParentConflictException(childOid, parentOid, conflictingParent);
            }
        }

        // get the parent
        LogicalObject parent = getParent(dao, parentOid);
        Preconditions.checkArgument(isFolder(parent.getObjectType()), "cannot insert into %s", parent.getObjectType());

        // check for name conflicts within the parent
        checkForNameConflicts(dao, parentOid, childName);

        LogicalObject child;

        // check if the caller is intending to create a new
        // object or simply use an existing object
        ObjectType storedChildObjectType = dao.objectTypes.get(childOid);
        if (storedChildObjectType != null) {
            Preconditions.checkArgument(childObjectType == null || childObjectType.equals(storedChildObjectType), "mismatched object type exp:%s act:%s", storedChildObjectType, childObjectType);
            child = dao.objects.get(childOid);
        } else {
            Preconditions.checkArgument(childObjectType != null, "%s does not exist", childOid);
            child = newObject(dao, parent.getRoot(), childOid, childObjectType);
        }

        // by this point the child object should exist
        Preconditions.checkArgument(child != null, "%s does not exist", childOid);

        // attach the object to the requested parent
        long transformTimestamp = attachChild(dao, device, parent, childOid, childName, atomic);

        LOGGER.info("insert {} into {}", childOid, parentOid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private static Updated renameChild(DAO dao, String device, String parentOid, String childOid, byte[] newChildName) throws PolarisException {
        // get the parent
        LogicalObject parent = getParent(dao, parentOid);

        // the child we're removing exists
        getExistingObject(dao, childOid);

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(dao.children.isChild(parentOid, childOid), "%s is not a child of %s", childOid, parentOid);

        // get the current child name (informational)
        byte[] currentChildName = dao.children.getChildName(parentOid, childOid);

        // check for name conflicts with the new name
        checkForNameConflicts(dao, parentOid, newChildName);

        // rename the child to the new name within the same tree
        long transformTimestamp = renameChild(dao, device, parent, childOid, newChildName);

        LOGGER.info("rename {} from {} to {} in {}", childOid, currentChildName, newChildName, parentOid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private static Updated removeChild(DAO dao, String device, String parentOid, String childOid, @Nullable Atomic atomic) throws PolarisException {
        Preconditions.checkArgument(!Identifiers.isRootStore(childOid), "cannot remove root store %s", childOid);

        // get the parent
        LogicalObject parent = getParent(dao, parentOid);

        // the child we're removing exists
        LogicalObject child = getExistingObject(dao, childOid);

        // the child we're removing is actually the child of this object
        byte[] childName = dao.children.getChildName(parentOid, childOid);
        Preconditions.checkArgument(childName != null, "%s is not a child of %s", childOid, parentOid);

        // detach the child from its parent
        long transformTimestamp = detachChild(dao, device, parent, childOid, atomic);

        // check if we should unroot the object
        if (dao.children.getActiveReferenceCount(childOid) == 0) {
            unrootObject(dao, child, childName);
        }

        LOGGER.info("remove {} from {}", childOid, parentOid);

        // return the latest version of the object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private static Updated makeContent(DAO dao, String device, String oid, long deviceVersion, String contentHash, long contentSize, long contentTime) throws PolarisException {
        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);

        // check that we're trying to add content for a file
        Preconditions.checkArgument(isFile(object.getObjectType()), "cannot add content for %s type", object.getObjectType());

        // check that we're at the right version
        if (deviceVersion != object.getVersion()) {
            throw new VersionConflictException(oid, deviceVersion, object.getVersion());
        }

        // create an entry for a new version of the content
        long transformTimestamp = newContent(dao, device, object, contentHash, contentSize, contentTime);

        // return the latest version of the object
        return new Updated(transformTimestamp, getExistingObject(dao, oid));
    }

    private static LogicalObject getParent(DAO dao, String oid) throws NotFoundException {
        // check if the parent exists
        LogicalObject parent = dao.objects.get(oid);

        // if it doesn't exist, and it's a shared folder, create it
        if (parent == null) {
            if (Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid)) {
                parent = newRoot(dao, oid);
            } else {
                throw new NotFoundException(oid);
            }
        }

        return parent;
    }

    private static void checkForNameConflicts(DAO dao, String parentOid, byte[] childName) throws NotFoundException, NameConflictException {
        String childOid = dao.children.getChildNamed(parentOid, childName);
        if (childOid != null) {
            throw new NameConflictException(parentOid, childName, getExistingObject(dao, childOid));
        }
    }

    //
    // primitive operations
    //
    // these methods do *not* check pre/post conditions
    //

    private static LogicalObject newRoot(DAO dao, String oid) {
        verifyStore(oid);

        return newObject(dao, oid, oid, ObjectType.ROOT);
    }

    private static LogicalObject newObject(DAO dao, String root, String oid, ObjectType objectType) {
        verifyStore(root);

        // create the object at the initial version
        dao.objects.add(root, oid, Constants.INITIAL_OBJECT_VERSION);

        // add a type-mapping for the object
        dao.objectTypes.add(oid, objectType);

        // return the newly created object
        return dao.objects.get(oid);
    }

    private static long attachChild(DAO dao, String device, LogicalObject parent, String childOid, byte[] childName, @Nullable Atomic atomic) {
        long newParentVersion = parent.getVersion() + 1;

        // add the transform
        long timestamp = System.currentTimeMillis();
        long logicalTimestamp = dao.transforms.add(device, parent.getRoot(), parent.getOid(), TransformType.INSERT_CHILD, newParentVersion, childOid, childName, timestamp, atomic);

        // update the version of the parent
        dao.objects.update(parent.getRoot(), parent.getOid(), newParentVersion);

        // create an entry for the child
        dao.children.add(parent.getOid(), childOid, childName);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static long detachChild(DAO dao, String device, LogicalObject parent, String childOid, @Nullable Atomic atomic) {
        long newParentVersion = parent.getVersion() + 1;

        // add the transform
        long timestamp = System.currentTimeMillis();
        long logicalTimestamp = dao.transforms.add(device, parent.getRoot(), parent.getOid(), TransformType.REMOVE_CHILD, newParentVersion, childOid, null, timestamp, atomic);

        // update the version of the parent
        dao.objects.update(parent.getRoot(), parent.getOid(), newParentVersion);

        // remove the entry for the child
        dao.children.remove(parent.getOid(), childOid);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static long renameChild(DAO dao, String device, LogicalObject parent, String childOid, byte[] childName) {
        long newParentVersion = parent.getVersion() + 1;

        // add the transform
        long timestamp = System.currentTimeMillis();
        long logicalTimestamp = dao.transforms.add(device, parent.getRoot(), parent.getOid(), TransformType.RENAME_CHILD, newParentVersion, childOid, childName, timestamp, null);

        // update the version of the parent
        dao.objects.update(parent.getRoot(), parent.getOid(), newParentVersion);

        // update the child entry in the children table
        dao.children.update(parent.getOid(), childOid, childName);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static long newContent(DAO dao, String device, LogicalObject file, String hash, long size, long mtime) {
        long newVersion = file.getVersion() + 1;

        // add an entry in the transforms table
        long timestamp = System.currentTimeMillis();
        long logicalTimestamp = dao.transforms.add(device, file.getRoot(), file.getOid(), TransformType.UPDATE_CONTENT, newVersion, null, null, timestamp, null);

        // add a row to the content table
        dao.objectProperties.add(file.getOid(), newVersion, hash, size, mtime);

        // update the version for the object
        dao.objects.update(file.getRoot(), file.getOid(), newVersion);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static void unrootObject(DAO dao, LogicalObject logicalObject, byte[] name) {
        dao.children.add(Constants.NO_ROOT, logicalObject.getOid(), name); // concat
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // object location updates
    //
    //------------------------------------------------------------------------------------------------------------------

    public List<String> getLocations(DAO dao, String user, String oid, long version) throws PolarisException {
        checkAccess(dao, user, oid, Access.READ);

        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);
        checkVersionInRange(object, version);

        // check that the object is a file
        ObjectType objectType = dao.objectTypes.get(oid);
        Preconditions.checkState(objectType != null, "no object type for %s", oid);
        Preconditions.checkArgument(isFile(objectType), "cannot add content for %s", objectType);

        List<String> existingLocations = Lists.newArrayListWithCapacity(10);

        // now, let's get the list of devices that have this content
        try (ResultIterator<String> iterator = dao.locations.get(oid, version)) {
            while (iterator.hasNext()) {
                existingLocations.add(iterator.next());
            }
        }

        return existingLocations;
    }

    public void performLocationUpdate(DAO dao, String user, LocationUpdateType updateType, String oid, long version, String did) throws PolarisException {
        checkAccess(dao, user, oid, Access.READ, Access.WRITE);

        switch (updateType) {
            case INSERT:
                insertLocation(dao, oid, version, did);
                break;
            case REMOVE:
                removeLocation(dao, oid, version, did);
                break;
            default:
                throw new IllegalArgumentException("unhandled location update type " + updateType.name());
        }
    }

    private static void insertLocation(DAO dao, String oid, long version, String did) throws PolarisException {
        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);

        // check that the version looks right
        checkVersionInRange(object, version);

        // check that the object is a file
        ObjectType objectType = dao.objectTypes.get(oid);
        Preconditions.checkState(objectType != null, "no object type for %s", oid);
        Preconditions.checkArgument(isFile(objectType), "cannot add content for %s type", objectType);

        // now, let's add the new location for the object
        dao.locations.add(oid, version, did);
    }

    private static void removeLocation(DAO dao, String oid, long version, String did) throws PolarisException {
        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);

        // check that the version looks right
        checkVersionInRange(object, version);

        // check that the object is a file
        ObjectType objectType = dao.objectTypes.get(oid);
        Preconditions.checkState(objectType != null, "no object type for %s", oid);
        Preconditions.checkArgument(isFile(objectType), "cannot add content for %s type", objectType);

        // now, let's remove the existing location for the object
        dao.locations.remove(oid, version, did);
    }

    private static LogicalObject getExistingObject(DAO dao, String oid) throws NotFoundException {
        LogicalObject object = dao.objects.get(oid);

        if (object == null) {
            throw new NotFoundException(oid);
        }

        return object;
    }

    private static boolean isFolder(ObjectType objectType) {
        return objectType == ObjectType.ROOT || objectType == ObjectType.FOLDER;
    }

    private static boolean isFile(ObjectType objectType) {
        return objectType == ObjectType.FILE;
    }

    private static void checkVersionInRange(LogicalObject object, long version) {
        Preconditions.checkArgument(version >= 0, "version %s less than 0", version);
        Preconditions.checkArgument(version <= object.getVersion(), "version %s exceeds upper bound of %s", version, object.getVersion());
    }

    private static void verifyStore(String oid) {
        Preconditions.checkArgument(Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid), "%s not an sid", oid);
    }
}
