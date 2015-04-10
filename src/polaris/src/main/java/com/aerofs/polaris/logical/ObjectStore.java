package com.aerofs.polaris.logical;

import com.aerofs.baseline.db.TransactionIsolation;
import com.aerofs.ids.DID;
import com.aerofs.ids.Identifiers;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UniqueID;
import com.aerofs.ids.UserID;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.batch.location.LocationUpdateType;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.*;
import com.aerofs.polaris.dao.Atomic;
import com.aerofs.polaris.dao.types.LockableLogicalObject;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.ResultIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

// FIXME (AG): this is such a shitty, shitty piece of code: methods have a ton of parameters, the code looks ugly, it's painful

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
 * The methods in this class <strong>SHOULD NOT</strong>
 * be called within a transaction.
 * <br>
 * The methods in this class are re-entrant.
 *
 * <h3>Implementation Note</h3>
 * All methods in this class should only be called after performing
 * access checks. This is enforced by having all public methods
 * do an access check and only then perform the requested operation in a
 * transaction. Unfortunately this means that callers cannot, say,
 * <ul>
 *     <li>Insert an object.</li>
 *     <li>Update content.</li>
 *     <li>Update content location.</li>
 * </ul>
 * all in a single transaction. But, maybe, it would
 * be nice if they could, someday... So, the implementation
 * here is designed to support that.
 * <br>
 * You will notice that all public methods have the same form:
 * <pre>
 *      METHOD:
 *          AccessToken <- checkAccess(....)
 *
 *          START_TRANSACTION:
 *              privateMethod0(AccessToken, parameters...)
 *              privateMethod1(AccessToken, parameters...)
 *              ...
 * </pre>
 * If you need to, you can expose all the privateMethodXXX(AccessToken, ...)
 * calls, do a single access check externally using the {@link #checkAccess(UserID, UniqueID, Access...)}
 * call and then chain as many db operations as you'd like in
 * a single {@link #inTransaction(StoreTransaction)} method call.
 */
@ThreadSafe
@Singleton
public final class ObjectStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStore.class);

    private final AccessManager accessManager;
    private final DBI dbi;
    private final StoreMigrator migrator;

    /**
     * Constructor.
     *
     * @param accessManager implementation of {@code AccessManager} used to check if a user can access a logical object
     * @param dbi database wrapper used to read/update the logical object database
     */
    @Inject
    public ObjectStore(AccessManager accessManager, DBI dbi, StoreMigrator migrator) {
        this.accessManager = accessManager;
        this.dbi = dbi;
        this.migrator = migrator;
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // transaction wrappers
    //
    //------------------------------------------------------------------------------------------------------------------

    /**
     * Perform a set of operations (query, update) on the
     * object store within a single transaction at the defaul
     * isolation level.
     *
     * @param transaction anonymous class containing the operations to invoke on the object store
     * @param <ReturnType> type returned by {@code transaction} (can be {@link Void}).
     * @return an instance of {@code ReturnType} containing the result of {@code transaction}
     * @throws org.skife.jdbi.v2.exceptions.CallbackFailedException if {@code transaction} could not complete successfully
     */
    public <ReturnType> ReturnType inTransaction(StoreTransaction<ReturnType> transaction) {
        return dbi.inTransaction((conn, status) -> {
            DAO dao = new DAO(conn);
            return transaction.execute(dao);
        });
    }

    /**
     * Perform a set of operations (query, update) on the
     * object store within a single transaction at the specified
     * {@code isolationLevel}.
     *
     * @param transaction anonymous class containing the operations to invoke on the object store
     * @param isolationLevel transaction isolation level at which to run the transaction
     * @param <ReturnType> type returned by {@code transaction} (can be {@link Void}).
     * @return an instance of {@code ReturnType} containing the result of {@code transaction}
     * @throws org.skife.jdbi.v2.exceptions.CallbackFailedException if {@code transaction} could not complete successfully
     */
    public <ReturnType> ReturnType inTransaction(StoreTransaction<ReturnType> transaction, TransactionIsolation isolationLevel) {
        return dbi.inTransaction((conn, status) -> {
            conn.setTransactionIsolation(isolationLevel.getLevel());
            DAO dao = new DAO(conn);
            return transaction.execute(dao);
        });
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // access control
    //
    //------------------------------------------------------------------------------------------------------------------

    public static final class AccessToken {

        public final UserID user;
        public final UniqueID store;
        public final Set<Access> granted;

        // private so that it can *only* be created from within this class
        private AccessToken(UserID user, UniqueID store, Access... granted) {
            Preconditions.checkArgument(granted.length > 0, "at least access type must be allowed");
            this.user = user;
            this.store = store;
            this.granted = ImmutableSet.copyOf(granted);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AccessToken other = (AccessToken) o;
            return Objects.equal(user, other.user) && Objects.equal(store, other.store) && Objects.equal(granted, other.granted);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(user, store, granted);
        }

        @Override
        public String toString() {
            return Objects
                    .toStringHelper(this)
                    .add("user", user)
                    .add("store", store)
                    .add("granted", granted)
                    .toString();
        }

        // granted must be a *superset* of requested
        public boolean subsumes(Access[] requested) {
            if (requested.length == 0) {
                return false;
            }

            boolean subsumes = true;

            for (Access required : requested) {
                if (!granted.contains(required)) {
                    subsumes = false;
                }
            }

            return subsumes;
        }
    }

    /**
     * Check if an object can be accessed by a user.
     *
     * @param user user id for which permissions should be checked
     * @param oid object the user wants to access
     * @param requested permissions the user wants on the object
     * @throws NotFoundException if the object does not exist
     * @throws AccessException if the user cannot access the object
     */
    public AccessToken checkAccess(UserID user, UniqueID oid, Access... requested) throws NotFoundException, AccessException {
        Preconditions.checkArgument(requested.length > 0, "at least one Access type required");

        UniqueID store = inTransaction(dao -> getStore(dao, oid));

        // avoid checking sparta if it turns out that this is some user's root store
        if (Identifiers.isRootStore(store) && SID.rootSID(user).equals(store)) {
            return new AccessToken(user, store, requested);
        }

        accessManager.checkAccess(user, store, requested);
        return new AccessToken(user, store, requested);
    }

    /* (non-javadoc)
     *
     * Check if the access token we were previously granted is sufficient to perform the requested operation.
     */
    private void checkAccessGranted(DAO dao, AccessToken accessToken, UniqueID oid, Access... requested) throws NotFoundException, AccessException {
        Preconditions.checkArgument(requested.length > 0, "at least one Access type required");

        UniqueID currentStore = getStore(dao, oid);
        Preconditions.checkArgument(currentStore.equals(accessToken.store), "access granted for store %s instead of %s", accessToken.store, currentStore);

        if (!accessToken.subsumes(requested)) {
            LOGGER.warn("access granted for {} instead of {}", accessToken.granted, requested);
            throw new AccessException(accessToken.user, accessToken.store, requested);
        }
    }

    private UniqueID getStore(DAO dao, UniqueID oid) throws NotFoundException {
        UniqueID store;

        if (Identifiers.isSharedFolder(oid) || Identifiers.isRootStore(oid)) {
            store = oid;
        } else {
            LogicalObject object = dao.objects.get(oid);

            if (object == null) {
                throw new NotFoundException(oid);
            }

            store = object.store;
        }

        return store;
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // logical object transformations
    //
    //------------------------------------------------------------------------------------------------------------------

    /**
     * Get up to {@code maxReturnedResultCount} transforms starting at
     * {@code startTimestamp} for the shared folder or store store identified
     * by {@code store}.
     *
     * @param user user id of the user requesting the transforms
     * @param store shared folder or root store for which you want to get a list of transforms
     * @param startTimestamp logical timestamp <em>after</em> which to start retrieving transforms
     * @param maxReturnedResultCount maximum number of transforms to return
     * @return up to {@code maxReturnedResultCount} transforms for {@code store} starting at {@code startTimestamp}
     * @throws NotFoundException if the {@code store} for which transforms should be retrieved does not exist
     * @throws AccessException if {@code user} cannot list transforms for {@code store}
     */
    public Transforms getTransforms(UserID user, UniqueID store, long startTimestamp, long maxReturnedResultCount) throws NotFoundException, AccessException {
        AccessToken accessToken = checkAccess(user, store, Access.READ);
        return inTransaction(dao -> {
            long available = getTransformCount(dao, accessToken, store);
            List<Transform> transforms = getTransforms(dao, accessToken, store, startTimestamp, maxReturnedResultCount);
            return new Transforms(available, transforms);
        });
    }

    private long getTransformCount(DAO dao, AccessToken accessToken, UniqueID store) throws NotFoundException, AccessException {
        checkAccessGranted(dao, accessToken, store, Access.READ);
        verifyStore(store);
        return dao.transforms.getTransformCount(store);
    }

    private List<Transform> getTransforms(DAO dao, AccessToken accessToken, UniqueID store, long startTimestamp, long maxReturnedResultCount) throws NotFoundException, AccessException {
        checkAccessGranted(dao, accessToken, store, Access.READ);
        verifyStore(store);

        List<Transform> returned = Lists.newArrayList();

        try (ResultIterator<Transform> iterator = dao.transforms.getTransformsSince(startTimestamp, store)) {
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
     * @param user user id of the user making the change to the object
     * @param device device that submitted the change
     * @param oid object being transformed
     * @param operation high-level transformation (one of the types listed above, along with their relevant parameters)
     * @return list of primitive transformations that resulted from this operation
     * @throws NotFoundException if the {@code object} to be transformed (or one of its dependents) does not exist
     * @throws AccessException if {@code user} cannot transform {@code object} or one of its dependents
     */
    public OperationResult performTransform(UserID user, DID device, UniqueID oid, Operation operation) throws NotFoundException, AccessException {
        AccessToken accessToken = checkAccess(user, oid, Access.READ, Access.WRITE);
        return inTransaction(dao -> performTransform(dao, accessToken, device, oid, operation));
    }

    private OperationResult performTransform(DAO dao, AccessToken accessToken, DID device, UniqueID oid, Operation operation) throws NotFoundException, AccessException, ParentConflictException, NameConflictException, VersionConflictException {
        checkAccessGranted(dao, accessToken, oid, Access.READ, Access.WRITE);

        List<Updated> updated = Lists.newArrayListWithExpectedSize(2);
        UniqueID jobID = null;

        switch (operation.type) {
            case INSERT_CHILD: {
                InsertChild ic = (InsertChild) operation;
                updated.add(insertChild(dao, device, oid, ic.child, ic.childObjectType, ic.childName, false, null));
                break;
            }
            case MOVE_CHILD: {
                MoveChild mc = (MoveChild) operation;
                if (oid.equals(mc.newParent)) {
                    updated.add(renameChild(dao, device, oid, mc.child, mc.newChildName));
                } else {
                    Atomic atomic = new Atomic(2);
                    updated.add(insertChild(dao, device, mc.newParent, mc.child, null, mc.newChildName, true, atomic));
                    updated.add(removeChild(dao, device, oid, mc.child, atomic));
                    Preconditions.checkState(updated.get(0).object.store.equals(updated.get(1).object.store), "cannot move object %s across store boundaries", mc.child);
                }
                break;
            }
            case REMOVE_CHILD: {
                RemoveChild rc = (RemoveChild) operation;
                updated.add(removeChild(dao, device, oid, rc.child, null));
                break;
            }
            case UPDATE_CONTENT: {
                UpdateContent uc = (UpdateContent) operation;
                updated.add(makeContent(dao, device, oid, uc.localVersion, uc.hash, uc.size, uc.mtime));
                break;
            }
            case SHARE: {
                updated.add(changeToStore(dao, device, oid));
                jobID = migrator.migrateStore(SID.folderOID2convertedStoreSID(new OID(oid)), device);
                break;
            }
            default:
                throw new IllegalArgumentException("unsupported operation " + operation.type);
        }

        return new OperationResult(updated, jobID);
    }

    private static Updated insertChild(DAO dao, DID device, UniqueID parentOid, UniqueID childOid, @Nullable ObjectType childObjectType, byte[] childName, boolean allowReinsert, @Nullable Atomic atomic) throws ParentConflictException, NotFoundException, NameConflictException {
        UniqueID currentParentOid = dao.children.getParent(childOid);

        if (currentParentOid != null) {
            LogicalObject currentParent = dao.objects.get(currentParentOid);
            Preconditions.checkState(currentParent != null, "current parent with oid %s could not be found", currentParentOid);
            // check if this operation would be a no-op
            if (parentOid.equals(currentParentOid)) {
                LOGGER.info("no-op for insertChild operation inserting {} into {}", childOid, parentOid);
                long matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(currentParent.store, currentParent.oid, TransformType.INSERT_CHILD, childOid);
                Preconditions.checkState(matchingTransform != 0L, "could not find transform inserting %s as a child of %s", childOid, currentParentOid);
                return new Updated(matchingTransform, currentParent);
            // check if the object is not a mount point and is being reinserted
            } else if (!allowReinsert && !ObjectType.STORE.equals(childObjectType) && !currentParentOid.equals(OID.TRASH)) {
                throw new ParentConflictException(childOid, parentOid, currentParent);
            }
        }

        // get the parent
        LockableLogicalObject parent = getUnlockedParent(dao, parentOid);
        Preconditions.checkArgument(isFolder(parent.objectType), "cannot insert into %s", parent.objectType);

        // check for name conflicts within the parent
        checkForNameConflicts(dao, parentOid, childName);

        LogicalObject child;
        // check if the caller is intending to create a new object or simply use an existing object
        ObjectType storedChildObjectType = dao.objectTypes.get(childOid);
        if (storedChildObjectType != null) {
            Preconditions.checkArgument(childObjectType == null || childObjectType.equals(storedChildObjectType), "mismatched object type exp:%s act:%s", storedChildObjectType, childObjectType);
            child = dao.objects.get(childOid);
        } else {
            Preconditions.checkArgument(childObjectType == ObjectType.FOLDER || childObjectType == ObjectType.FILE, "new child object %s can not be created, as it is not a folder or file", childOid);
            child = newObject(dao, parent.store, childOid, childObjectType);
        }

        // by this point the child object should exist
        Preconditions.checkArgument(child != null, "%s does not exist", childOid);

        if(childObjectType == ObjectType.STORE) {
            Preconditions.checkState(isInRootStore(dao, parentOid), "can only insert mount points into user root stores");
        }

        // attach the object to the requested parent
        long transformTimestamp = attachChild(dao, device, parent, child, childName, atomic);

        LOGGER.info("insert {} into {}", childOid, parentOid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private static Updated renameChild(DAO dao, DID device, UniqueID parentOid, UniqueID childOid, byte[] newChildName) throws NotFoundException, NameConflictException {
        // get the parent
        LockableLogicalObject parent = getUnlockedParent(dao, parentOid);

        // the child we're removing exists
        getExistingObject(dao, childOid);

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(dao.children.isChild(parentOid, childOid), "%s is not a child of %s", childOid, parentOid);

        // get the current child name (informational)
        byte[] currentChildName = dao.children.getChildName(parentOid, childOid);
        if (Arrays.equals(newChildName, currentChildName)) {
            LOGGER.info("no-op for renameChild operation on child {} of {}", childOid, parentOid);
            long matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(parent.store, parent.oid, TransformType.RENAME_CHILD, childOid);
            Preconditions.checkState(matchingTransform != 0L, "could not find transform renaming %s as a child of %s", childOid, parentOid);
            return new Updated(matchingTransform, parent);
        }

        // check for name conflicts with the new name
        checkForNameConflicts(dao, parentOid, newChildName);

        // rename the child to the new name within the same tree
        long transformTimestamp = renameChild(dao, device, parent, childOid, newChildName);

        LOGGER.info("rename {} from {} to {} in {}", childOid, currentChildName, newChildName, parentOid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private static Updated removeChild(DAO dao, DID device, UniqueID parentOid, UniqueID childOid, @Nullable Atomic atomic) throws NotFoundException {
        Preconditions.checkArgument(!Identifiers.isRootStore(childOid), "cannot remove root store %s", childOid);

        // get the parent
        LockableLogicalObject parent = getUnlockedParent(dao, parentOid);

        // the child we're removing exists
        LogicalObject child = getExistingObject(dao, childOid);

        // the child we're removing is actually the child of this object
        byte[] childName = dao.children.getChildName(parentOid, childOid);

        if (childName == null) {
            // child is not under this parent, may be a duplicate operation
            LOGGER.info("no-op for removeChild operation on child {} of {}", childOid, parentOid);
            long matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(parent.store, parent.oid, TransformType.REMOVE_CHILD, child.oid);
            Preconditions.checkArgument(matchingTransform != 0L, "%s is not a child of %s", childOid, parentOid);
            return new Updated(matchingTransform, parent);
        }

        // detach the child from its parent
        long transformTimestamp = detachChild(dao, device, parent, child, atomic);

        // check if we should unroot the object, though we don't unroot mount points (since their corresponding logical objects are stores)
        if (child.objectType != ObjectType.STORE && dao.children.getActiveReferenceCount(childOid) == 0) {
            removeObjectFromStore(dao, child, childName);
        }

        LOGGER.info("remove {} from {}", childOid, parentOid);

        // return the latest version of the object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private static Updated makeContent(DAO dao, DID device, UniqueID oid, long deviceVersion, byte[] contentHash, long contentSize, long contentTime) throws NotFoundException, VersionConflictException {
        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);

        // check that we're trying to add content for a file
        Preconditions.checkArgument(isFile(object.objectType), "cannot add content for %s type", object.objectType);

        // check if the new content matches the object's latest content (disregarding version)
        Content currentObjectContent = dao.objectProperties.getLatest(oid);
        if (currentObjectContent != null && currentObjectContent.equals(new Content(oid, object.version, contentHash, contentSize, contentTime))) {
            LOGGER.info("no-op for makeContent operation on {}", oid);
            long matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(object.store, oid, TransformType.UPDATE_CONTENT);
            Preconditions.checkState(matchingTransform != 0L, "could not find transform updating content of %s", oid);
            return new Updated(matchingTransform, object);
        }

        // check that we're at the right version
        if (deviceVersion != object.version) {
            throw new VersionConflictException(oid, deviceVersion, object.version);
        }

        // create an entry for a new version of the content
        long transformTimestamp = newContent(dao, device, object, contentHash, contentSize, contentTime);

        // return the latest version of the object
        return new Updated(transformTimestamp, getExistingObject(dao, oid));
    }

    private static Updated changeToStore(DAO dao, DID device, UniqueID folderOID) {
        // check that object exists
        LogicalObject folder = getExistingObject(dao, folderOID);

        // can only migrate normal folders in a user root
        // N.B. can't check the logicalObject's store value because we might be under another folder that just got migrated, and the changes haven't been propagated to this folder yet
        Preconditions.checkArgument(isInRootStore(dao, folderOID) && folder.objectType == ObjectType.FOLDER, "oid %s must be a folder in a user's root store", folderOID);

        // cannot share a folder that contains shared folders
        Preconditions.checkArgument(!containsSharedFolder(dao, folder.store, folderOID), "cannot share oid %s because it contains a shared folder", folderOID);

        // need to replace the old folder transparently, which requires its name
        UniqueID parentOID = dao.children.getParent(folderOID);
        Preconditions.checkState(parentOID!= null, "folder to be migrated does not have a parent");
        byte[] folderName = dao.children.getChildName(parentOID, folderOID);
        Preconditions.checkState(folderName != null, "folder to be migrated does not have a name");

        // change the object type to mount
        long timestamp = convertToAnchor(dao, device, folder, folderName, parentOID);
        newStore(dao, SID.folderOID2convertedStoreSID(new OID(folderOID)));

        return new Updated(timestamp, getExistingObject(dao, folderOID));
    }

    private static LockableLogicalObject getParent(DAO dao, UniqueID oid) throws NotFoundException {
        // check if the parent exists
        LockableLogicalObject parent = dao.objects.get(oid);

        // if it doesn't exist, and it's a shared folder, create it
        if (parent == null) {
            if (Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid)) {
                parent = newStore(dao, oid);
            } else {
                throw new NotFoundException(oid);
            }
        }

        return parent;
    }

    private static void checkForNameConflicts(DAO dao, UniqueID parentOid, byte[] childName) throws NotFoundException, NameConflictException {
        UniqueID childOid = dao.children.getChildNamed(parentOid, childName);
        if (childOid != null) {
            throw new NameConflictException(parentOid, childName, getExistingObject(dao, childOid));
        }
    }

    private static boolean containsSharedFolder(DAO dao, UniqueID userRootStore, UniqueID oid) {
        try (ResultIterator<UniqueID> mountPoints =  dao.mountPoints.listUserMountPointParents(userRootStore)) {
            while (mountPoints.hasNext()) {
                UniqueID mountPointParent = mountPoints.next();
                while (!(Identifiers.isRootStore(mountPointParent) || oid.equals(mountPointParent))) {
                    mountPointParent = dao.children.getParent(mountPointParent);
                    Preconditions.checkArgument(mountPointParent != null, "cannot locate all mount points within user store %s", userRootStore);
                }
                if (oid.equals(mountPointParent)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean isInRootStore(DAO dao, UniqueID oid)
    {
        if (Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid) || OID.TRASH.equals(oid)) {
            return Identifiers.isRootStore(oid);
        }
        UniqueID parentID = dao.children.getParent(oid);
        Preconditions.checkArgument(parentID != null, "cannot find parent of oid %s", oid);
        return isInRootStore(dao, parentID);
    }

    //
    // primitive operations
    //
    // these methods do *not* check pre/post conditions
    //

    private static LockableLogicalObject newStore(DAO dao, UniqueID oid) {
        verifyStore(oid);

        return newObject(dao, oid, oid, ObjectType.STORE);
    }

    private static LockableLogicalObject newObject(DAO dao, UniqueID store, UniqueID oid, ObjectType objectType) {
        verifyStore(store);

        // create the object at the initial version
        dao.objects.add(store, oid, Constants.INITIAL_OBJECT_VERSION);

        // add a type-mapping for the object
        dao.objectTypes.add(oid, objectType);

        // return the newly created object
        return dao.objects.get(oid);
    }

    private static long attachChild(DAO dao, DID device, LogicalObject parent, LogicalObject child, byte[] childName, @Nullable Atomic atomic) {
        long newParentVersion = parent.version + 1;

        // add entry in transforms table and update our latest known max logical timestamp
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, parent.store, parent.oid, TransformType.INSERT_CHILD, newParentVersion, child.oid, childName, atomic);

        // update the version of the parent
        dao.objects.update(parent.store, parent.oid, newParentVersion);

        // create an entry for the child
        dao.children.add(parent.oid, child.oid, childName);

        // update mountpoints table if adding a new mount point
        if (child.objectType == ObjectType.STORE) {
            dao.mountPoints.add(parent.store, child.oid, parent.oid);
        }

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static long detachChild(DAO dao, DID device, LogicalObject parent, LogicalObject child, @Nullable Atomic atomic) {
        long newParentVersion = parent.version + 1;

        // add entry in transforms table and update our latest known max logical timestamp
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, parent.store, parent.oid, TransformType.REMOVE_CHILD, newParentVersion, child.oid, null, atomic);

        // update the version of the parent
        dao.objects.update(parent.store, parent.oid, newParentVersion);

        // remove the entry for the child
        dao.children.remove(parent.oid, child.oid);

        // update mountpoints table if the removed child was a mount point
        if (child.objectType == ObjectType.STORE) {
            dao.mountPoints.remove(parent.store, child.oid);
        }

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static long renameChild(DAO dao, DID device, LogicalObject parent, UniqueID childOid, byte[] childName) {
        long newParentVersion = parent.version + 1;

        // add entry in transforms table and update our latest known max logical timestamp
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, parent.store, parent.oid, TransformType.RENAME_CHILD, newParentVersion, childOid, childName, null);

        // update the version of the parent
        dao.objects.update(parent.store, parent.oid, newParentVersion);

        // update the child entry in the children table
        dao.children.update(parent.oid, childOid, childName);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static long newContent(DAO dao, DID device, LogicalObject file, byte[] hash, long size, long mtime) {
        long newVersion = file.version + 1;

        // add entry in transforms table and update our latest known max logical timestamp
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, file.store, file.oid, TransformType.UPDATE_CONTENT, newVersion, null, null, null);

        // add a row to the content table
        dao.objectProperties.add(file.oid, newVersion, hash, size, mtime);

        // update the version for the object
        dao.objects.update(file.store, file.oid, newVersion);

        // add the location of the new content
        dao.locations.add(file.oid, newVersion, device);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static long convertToAnchor(DAO dao, DID device, LogicalObject folder, byte[] folderName, UniqueID parent) {
        long newVersion = folder.version + 1;

        // add entry in transforms table and update our latest known max logical timestamp
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, folder.store, folder.oid, TransformType.SHARE, newVersion, null, null, null);

        // replace the old folder's references with the new mount point's
        UniqueID anchorOID = SID.folderOID2convertedAnchorOID(new OID(folder.oid));
        dao.children.remove(parent, folder.oid);
        dao.children.add(parent, anchorOID, folderName);
        removeObjectFromStore(dao, folder, folderName);

        dao.objects.update(folder.store, folder.oid, newVersion);

        dao.mountPoints.add(folder.store, anchorOID, parent);

        return logicalTimestamp;
    }

    private static long addTransformAndUpdateMaxLogicalTimestamp(DAO dao, DID device, UniqueID store, UniqueID oid, TransformType transformType, long newVersion, @Nullable UniqueID child, @Nullable byte[] name, @Nullable Atomic atomic) {
        // add an entry in the transforms table
        long timestamp = System.currentTimeMillis();
        long logicalTimestamp = dao.transforms.add(device, store, oid, transformType, newVersion, child, name, timestamp, atomic);

        // update the store max timestamp
        dao.logicalTimestamps.updateLatest(store, logicalTimestamp);

        // return logical timestamp associated with this transform
        return logicalTimestamp;
    }

    private static void removeObjectFromStore(DAO dao, LogicalObject logicalObject, byte[] name) {
        byte[] hex = PolarisUtilities.stringToUTF8Bytes(PolarisUtilities.hexEncode(logicalObject.oid.getBytes()));
        Preconditions.checkState(hex != null, "%s could not be converted to hex string", logicalObject.oid);
        byte[] disambiguated = concat(hex, name);
        dao.children.add(OID.TRASH, logicalObject.oid, disambiguated);
    }

    private static byte[] concat(byte[] b0, byte[] b1) {
        byte[] concatenated = new byte[b0.length + b1.length];
        System.arraycopy(b0, 0, concatenated, 0, b0.length);
        System.arraycopy(b1, 0, concatenated, b0.length, b1.length);
        return concatenated;
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // object location updates
    //
    //------------------------------------------------------------------------------------------------------------------

    /**
     * Get a list of devices at which {@code oid}, {@code version} is available.
     *
     * @param user user id requesting the list of devices
     * @param oid object to be located
     * @param version integer version > 0 of {@code oid}
     * @return list of devices at which {@code oid}, {@code version} can be found
     * @throws NotFoundException if the {@code object} to be located does not exist
     * @throws AccessException if {@code user} cannot retrieve the list of locations for {@code oid}, {@code version}
     */
    public List<DID> getLocations(UserID user, UniqueID oid, long version) throws NotFoundException, AccessException {
        AccessToken accessToken = checkAccess(user, oid, Access.READ);
        return inTransaction(dao -> getLocations(dao, accessToken, oid, version));
    }

    private List<DID> getLocations(DAO dao, AccessToken accessToken, UniqueID oid, long version) throws NotFoundException, AccessException {
        checkAccessGranted(dao, accessToken, oid, Access.READ);

        // check that the object exists
        LogicalObject object = getExistingObject(dao, oid);
        checkVersionInRange(object, version);

        // check that the object is a file
        ObjectType objectType = dao.objectTypes.get(oid);
        Preconditions.checkState(objectType != null, "no object type for %s", oid);
        Preconditions.checkArgument(isFile(objectType), "cannot add content for %s", objectType);

        List<DID> existingLocations = Lists.newArrayListWithCapacity(10);

        // now, let's get the list of devices that have this content
        try (ResultIterator<DID> iterator = dao.locations.get(oid, version)) {
            while (iterator.hasNext()) {
                existingLocations.add(iterator.next());
            }
        }

        return existingLocations;
    }

    /**
     * Update the list of devices at which an object
     * identified by {@code oid}, {@code version} can be located.
     * Updates can be one of:
     * <ul>
     *     <li>Addition.</li>
     *     <li>Removal.</li>
     * </ul>
     *
     * @param user user id updating the list of devices
     * @param updateType {@link LocationUpdateType#INSERT} to add {@code did} to
     *                   the list of devices or {@link LocationUpdateType#REMOVE}
     *                   to remove {@code did} from the list of devices
     * @param oid object for which the location list should be updated
     * @param version integer version > 0 of {@code oid}
     * @param did device id to be added or removed from the location list
     * @throws NotFoundException if the {@code object} for which the location list should be updated does not exist
     * @throws AccessException if {@code user} cannot update the list of locations for {@code oid}, {@code version}
     */
    public void performLocationUpdate(UserID user, LocationUpdateType updateType, UniqueID oid, long version, DID did) throws NotFoundException, AccessException {
        AccessToken accessToken = checkAccess(user, oid, Access.READ, Access.WRITE);
        inTransaction(dao -> {
            performLocationUpdate(dao, accessToken, updateType, oid, version, did);
            return null;
        });
    }

    private void performLocationUpdate(DAO dao, AccessToken accessToken, LocationUpdateType updateType, UniqueID oid, long version, DID did) throws NotFoundException, AccessException {
        checkAccessGranted(dao, accessToken, oid, Access.READ, Access.WRITE);

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

    private static void insertLocation(DAO dao, UniqueID oid, long version, DID did) throws NotFoundException {
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

    private static void removeLocation(DAO dao, UniqueID oid, long version, DID did) throws NotFoundException {
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

    private static LockableLogicalObject getExistingObject(DAO dao, UniqueID oid) throws NotFoundException {
        LockableLogicalObject object = dao.objects.get(oid);

        if (object == null) {
            throw new NotFoundException(oid);
        }

        return object;
    }

    private static LockableLogicalObject getUnlockedParent(DAO dao, UniqueID oid) throws ObjectLockedException {
        LockableLogicalObject parent = getParent(dao, oid);
        if (parent.locked) {
            throw new ObjectLockedException(oid);
        }
        return parent;
    }

    private static boolean isFolder(ObjectType objectType) {
        return objectType == ObjectType.STORE || objectType == ObjectType.FOLDER;
    }

    private static boolean isFile(ObjectType objectType) {
        return objectType == ObjectType.FILE;
    }

    private static void checkVersionInRange(LogicalObject object, long version) {
        Preconditions.checkArgument(version >= 0, "version %s less than 0", version);
        Preconditions.checkArgument(version <= object.version, "version %s exceeds upper bound of %s", version, object.version);
    }

    private static void verifyStore(UniqueID oid) {
        Preconditions.checkArgument(Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid), "%s not an sid", oid);
    }
}
