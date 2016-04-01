package com.aerofs.polaris.logical;

import com.aerofs.baseline.db.TransactionIsolation;
import com.aerofs.ids.*;
import com.aerofs.polaris.Constants;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.acl.AccessManager;
import com.aerofs.polaris.api.batch.location.LocationUpdateType;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.*;
import com.aerofs.polaris.dao.Atomic;
import com.aerofs.polaris.dao.LockStatus;
import com.aerofs.polaris.dao.types.LockableLogicalObject;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Sets;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.ResultIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

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
 * calls, do a single access check externally using the {@link #checkAccess(UserID, Collection, Access...)}
 * call and then chain as many db operations as you'd like in
 * a single {@link #inTransaction(StoreTransaction)} method call.
 */
@ThreadSafe
@Singleton
public final class ObjectStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStore.class);

    private final AccessManager accessManager;
    private final DBI dbi;
    private final Migrator migrator;
    private final ConcurrentMap<UniqueID, Lock> objectLocks;

    /**
     * Constructor.
     *
     * @param accessManager implementation of {@code AccessManager} used to check if a user can access a logical object
     * @param dbi database wrapper used to read/update the logical object database
     */
    @Inject
    public ObjectStore(AccessManager accessManager, DBI dbi, Migrator migrator) {
        this.accessManager = accessManager;
        this.dbi = dbi;
        this.migrator = migrator;
        objectLocks = new MapMaker().weakValues().makeMap();
    }

    //------------------------------------------------------------------------------------------------------------------
    //
    // transaction wrappers
    //
    //------------------------------------------------------------------------------------------------------------------

    /**
     * Perform a set of operations (query, update) on the
     * object store within a single transaction at the default
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
        public final Set<UniqueID> stores;
        public final Set<Access> granted;

        // private so that it can *only* be created from within this class
        private AccessToken(UserID user, Set<UniqueID> stores, Access... granted) {
            Preconditions.checkArgument(granted.length > 0, "at least access type must be allowed");
            this.user = user;
            this.stores = stores;
            this.granted = ImmutableSet.copyOf(granted);
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AccessToken other = (AccessToken) o;
            return Objects.equal(user, other.user) && Objects.equal(stores, other.stores) && Objects.equal(granted, other.granted);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(user, stores, granted);
        }

        @Override
        public String toString() {
            return Objects
                    .toStringHelper(this)
                    .add("user", user)
                    .add("stores", stores)
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
     * @param oids objects the user wants to access
     * @param requested permissions the user wants on the object
     * @throws NotFoundException if the object does not exist
     * @throws AccessException if the user cannot access the object
     */
    public AccessToken checkAccess(UserID user, Collection<UniqueID> oids, Access... requested) throws NotFoundException, AccessException {
        Preconditions.checkArgument(requested.length > 0, "at least one Access type required");

        Set<UniqueID> stores = inTransaction(dao -> {
            Set<UniqueID> s = Sets.newHashSet();
            s.addAll(oids.stream().map(oid -> getStore(dao, oid)).collect(Collectors.toList()));
            return s;
        }, TransactionIsolation.READ_COMMITTED);
        return checkAccessForStores(user, stores, requested);
    }

    // Pre-condition: Collection must be a set of stores.
    public AccessToken checkAccessForStores(UserID user, Set<UniqueID> stores, Access... requested)
    {
        Set<UniqueID> verified = Sets.newHashSet();

        Preconditions.checkArgument(stores.stream().allMatch(store -> Identifiers.isRootStore(store)
                        || Identifiers.isSharedFolder(store)),
                "At least one non store provided in collection.");
        // avoid checking sparta if it turns out that this is some user's root store
        verified.addAll(stores.stream().filter(store -> Identifiers.isRootStore(store) &&
                SID.rootSID(user).equals(store)).collect(Collectors.toList()));

        accessManager.checkAccess(user, Sets.difference(stores, verified), requested);
        return new AccessToken(user, stores, requested);
    }

    private AccessToken checkAccess(UserID user, UniqueID store, Access... requested) {
        return checkAccess(user, Lists.newArrayList(store), requested);
    }

    /* (non-javadoc)
     *
     * Check if the access token we were previously granted is sufficient to perform the requested operation.
     */
    private void checkAccessGranted(DAO dao, AccessToken accessToken, UniqueID oid, Access... requested) throws NotFoundException, AccessException {
        Preconditions.checkArgument(requested.length > 0, "at least one Access type required");

        UniqueID store = getStore(dao, oid);
        Preconditions.checkArgument(accessToken.stores.contains(store), "access not granted for store %s", store);

        if (!accessToken.subsumes(requested)) {
            LOGGER.warn("access granted for {} instead of {}", accessToken.granted, requested);
            throw new AccessException(accessToken.user, store, requested);
        }
    }

    public UniqueID getStore(DAO dao, UniqueID oid) throws NotFoundException {
        if (Identifiers.isSharedFolder(oid) || Identifiers.isRootStore(oid)) {
            return oid;
        } else {
            UniqueID store = dao.objects.getStore(oid);
            if (store == null) {
                throw new NotFoundException(oid);
            }
            return store;
        }
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
     * @param user user id of the user requesting the transforms, null if don't need to check auth
     * @param store shared folder or root store for which you want to get a list of transforms
     * @param startTimestamp logical timestamp <em>after</em> which to start retrieving transforms
     * @param maxReturnedResultCount maximum number of transforms to return
     * @return up to {@code maxReturnedResultCount} transforms for {@code store} starting at {@code startTimestamp}
     * @throws NotFoundException if the {@code store} for which transforms should be retrieved does not exist
     * @throws AccessException if {@code user} cannot list transforms for {@code store}
     */
    public Transforms getTransforms(@Nullable UserID user, UniqueID store, long startTimestamp, long maxReturnedResultCount) throws NotFoundException, AccessException {
        AccessToken accessToken = user == null ? new AccessToken(UserID.fromInternal("internal service"), Sets.newHashSet(store), Access.READ) : checkAccess(user, store, Access.READ);
        return inTransaction(dao -> {
            long available = dao.transforms.getLatestLogicalTimestamp();
            List<Transform> transforms = getTransforms(dao, accessToken, store, startTimestamp, maxReturnedResultCount);
            return new Transforms(available, transforms);
        }, TransactionIsolation.READ_COMMITTED);
    }

    private List<Transform> getTransforms(DAO dao, AccessToken accessToken, UniqueID store, long startTimestamp, long maxReturnedResultCount) throws NotFoundException, AccessException {
        checkAccessGranted(dao, accessToken, store, Access.READ);
        verifyStore(store);

        List<Transform> returned = Lists.newArrayList();

        try (ResultIterator<Transform> iterator = dao.transforms.getTransformsSince(startTimestamp, store, maxReturnedResultCount)) {
            while (iterator.hasNext()) {
                returned.add(iterator.next());
            }
        }

        return returned;
    }

    public Lock lockObject(UniqueID oid)
    {
        Lock l = new ReentrantLock();
        Lock prev = objectLocks.putIfAbsent(oid, l);
        l = prev == null ? l : prev;
        l.lock();
        return l;
    }

    public List<Lock> lockObjects(List<UniqueID> objects)
    {
        // consistent lock acquisition order to avoid deadlocks
        Collections.sort(objects);
        List<Lock> locks = Lists.newArrayList();
        for (UniqueID o : objects) {
            locks.add(lockObject(o));
        }
        return locks;
    }

    /**
     * Perform a high-level transformation on a logical object. This transformation can be an:
     * <ul>
     *     <li>Insertion</li>
     *     <li>Removal</li>
     *     <li>Move</li>
     *     <li>Content modification</li>
     *     <li>Sharing Operation</li>
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
        List<UniqueID> affectedObjects = Lists.newArrayList(operation.affectedOIDs());
        affectedObjects.add(oid);
        AccessToken accessToken = checkAccess(user, affectedObjects, Access.READ, Access.WRITE);
        List<Lock> locks = lockObjects(affectedObjects);
        try {
            return inTransaction(dao -> performTransform(dao, accessToken, device, oid, operation));
        } finally {
            for (Lock l : locks) {
                l.unlock();
            }
        }
    }

    public OperationResult performTransform(DAO dao, AccessToken accessToken, DID device, UniqueID oid, Operation operation) throws NotFoundException, AccessException, ParentConflictException, NameConflictException, VersionConflictException {
        checkAccessGranted(dao, accessToken, oid, Access.READ, Access.WRITE);

        List<Updated> updated = Lists.newArrayListWithExpectedSize(2);
        UniqueID jobID = null;

        switch (operation.type) {
            case INSERT_CHILD: {
                InsertChild ic = (InsertChild) operation;
                updated.add(insertChild(dao, device, oid, ic.child, ic.childObjectType, ic.childName, ic.migrant));
                break;
            }
            case MOVE_CHILD: {
                MoveChild mc = (MoveChild) operation;
                if (oid.equals(mc.newParent)) {
                    updated.add(renameChild(dao, device, oid, mc.child, mc.newChildName));
                } else {
                    if (getStore(dao, oid).equals(getStore(dao, mc.newParent))) {
                        updated.addAll(moveChild(dao, device, oid, mc.newParent, mc.child, mc.newChildName));
                    } else {
                        checkAccessGranted(dao, accessToken, mc.newParent, Access.READ, Access.WRITE);
                        OID newOid = OID.generate();
                        ObjectType childObjectType = dao.objectTypes.get(mc.child);
                        if (childObjectType == ObjectType.FILE) {
                            updated.add(migrateObject(dao, device, mc.newParent, newOid, mc.child, mc.newChildName));
                            updated.add(removeChild(dao, device, oid, mc.child, newOid));
                        } else {
                            updated.add(migrateObject(dao, device, mc.newParent, newOid, mc.child, mc.newChildName));
                            jobID = migrator.moveCrossStore(dao, mc.child, newOid, device);
                        }
                        dao.objects.setLocked(mc.child, LockStatus.MIGRATED);
                    }
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
                SID store = changeToStore(dao, oid, (Share) operation);
                jobID = migrator.migrateStore(dao, store, device);
                break;
            }
            case RESTORE: {
                updated.add(restoreObject(dao, device, oid));
                if (getExistingObject(dao, oid).locked == LockStatus.MIGRATED) {
                    jobID = migrator.restoreMigratedObjects(dao, oid, device);
                }
                break;
            }
            default:
                throw new IllegalArgumentException("unsupported operation " + operation.type);
        }

        return new OperationResult(updated, jobID);
    }

    /*
     * @return whether the child is found under the subtree root (crossing store boundaries if necessary)
     */
    private boolean isInSubtree(DAO dao, UniqueID subtreeRoot, UniqueID target)
    {
        while(!(Identifiers.isRootStore(target) || target.equals(subtreeRoot))) {
            if (Identifiers.isMountPoint(target)) {
                UniqueID rootStore = getStore(dao, subtreeRoot);
                if (!Identifiers.isRootStore(rootStore)) {
                    // only subtree roots in the root store can contain an anchor (and thus cross store boundaries)
                    return false;
                }
                target = dao.mountPoints.getMountPointParent(rootStore, target);
                if (target == null) {
                    return false;
                }
            } else {
                target = dao.children.getParent(target);
                if (target == null) {
                    return false;
                }
            }
        }
        return target.equals(subtreeRoot);
    }

    private Updated restoreObject(DAO dao, DID device, UniqueID oid)
    {
        // no no-op detection since restore isn't an operation generated by daemons
        Preconditions.checkArgument(!Identifiers.isMountPoint(oid) && !Identifiers.isRootStore(oid), "cannot restore mountpoint or root store %s", oid);

        // can't restore a locked object
        LockableLogicalObject deletedObject = getExistingObject(dao, oid);
        if (deletedObject.locked == LockStatus.LOCKED) {
            throw new ObjectLockedException(oid);
        }

        UniqueID parentOid = dao.children.getParent(oid);
        Preconditions.checkArgument(parentOid != null, "could not find parent of object to restore %s", oid);
        Preconditions.checkArgument(dao.children.isDeleted(parentOid, oid), "cannot restore object %s because it is not deleted", oid);

        byte[] childName = dao.children.getChildName(parentOid, oid);
        Preconditions.checkState(childName != null, "could not find name of deleted object %s", oid);
        checkForNameConflicts(dao, parentOid, childName);

        // db row will be correctly regenerated by attachChild
        dao.children.remove(parentOid, oid);
        long transformTimestamp = attachChild(dao, device, getExistingObject(dao, parentOid), deletedObject, childName, null, null);

        LOGGER.info("restore {} into {}", oid, parentOid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private Updated insertChild(DAO dao, DID device, UniqueID parentOid, UniqueID childOid, @Nullable ObjectType childObjectType, byte[] childName, @Nullable UniqueID migrant) throws ParentConflictException, NotFoundException, NameConflictException
    {
        // parentOid is also guaranteed to be part of the root store if the store contains a mountpoint, otherwise would be a cross-store migration
        UniqueID currentParentOid = Identifiers.isMountPoint(childOid) ? dao.mountPoints.getMountPointParent(getStore(dao, parentOid), childOid) : dao.children.getParent(childOid);
        // check if this operation would be a no-op
        if (parentOid.equals(currentParentOid)) {
            if (!Arrays.equals(dao.children.getActiveChildName(parentOid, childOid), childName)) {
                throw new NameConflictException(parentOid, childName, childOid);
            }
            LogicalObject parent = dao.objects.get(parentOid);
            Preconditions.checkState(parent != null, "current parent with oid %s could not be found", parentOid);
            long matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(parent.store, parent.oid, childOid);
            if (matchingTransform == 0L && Identifiers.isMountPoint(childOid)) {
                // could also be the result of a share operation
                matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(parent.store, SID.anchorOID2folderOID(new OID(childOid)));
            }
            if (matchingTransform != 0L) {
                LOGGER.info("no-op for insertChild operation inserting {} into {}", childOid, parentOid);
                return new Updated(matchingTransform, parent);
            }
        }
        if (currentParentOid != null) {
            // currentParentOid will be non-null for deleted files and folders (but not anchors) as well as objects under another parent, so no re-insertions of deleted objects
            throw new ParentConflictException(childOid, parentOid, currentParentOid);
        }

        // parent must be unlocked so we don't insert an object that could miss migration
        LogicalObject parent = getUnlockedParent(dao, parentOid);
        Preconditions.checkArgument(isFolder(parent.objectType), "cannot insert into %s", parent.objectType);

        // check for name conflicts within the parent
        checkForNameConflicts(dao, parentOid, childName);

        LogicalObject child;
        // check if the caller is intending to create a new object or simply use an existing object
        ObjectType storedChildObjectType = dao.objectTypes.get(childOid);
        if (storedChildObjectType != null) {
            Preconditions.checkArgument(childObjectType == null || childObjectType.equals(storedChildObjectType), "mismatched object type exp:%s act:%s", storedChildObjectType, childObjectType);
            child = getUnlockedObject(dao, childOid);
            Preconditions.checkArgument(child.store.equals(childObjectType == ObjectType.STORE ? childOid : parent.store), "reinserting object %s under %s with conflicting store %s", childOid, parent, child.store);
            childObjectType = storedChildObjectType;
        } else {
            child = newObject(dao, childObjectType == ObjectType.STORE ? childOid : parent.store, childOid, childObjectType);
        }

        // by this point the child object should exist
        Preconditions.checkArgument(child != null, "%s does not exist", childOid);

        if (childObjectType == ObjectType.STORE) {
            Preconditions.checkArgument(isInRootStore(dao, parentOid), "can only insert mount points into user root stores");
        }

        LogicalObject migrantObject = null;
        if (migrant != null) {
            // NB: the client cannot know with absolute certainty whether its local objects are
            // present on polaris so it errs on the side of caution and may sometimes provide
            // migrant OIDs that do not in fact exist in polaris (e.g. when a folder is created,
            // shared and migrated out in quick succession)
            migrantObject = dao.objects.get(migrant);
            if (migrantObject != null) {
                Preconditions.checkArgument(migrantObject.objectType.equals(child.objectType)
                        || (child.objectType == ObjectType.FOLDER && migrantObject.objectType == ObjectType.STORE),
                        "migrant %s has different objecttype from new copy %s", migrant, childOid);
            }
        }

        // attach the object to the requested parent
        long transformTimestamp = attachChild(dao, device, parent, child, childName, migrant, null);
        if (migrantObject != null && migrantObject.objectType.equals(ObjectType.FILE) && migrantObject.version > Constants.INITIAL_OBJECT_VERSION) {
            Content content = dao.objectProperties.getLatest(migrant);
            Preconditions.checkState(content != null, "could not find content for migrant file %s", migrant);
            dao.objectProperties.add(childOid, content.version, content.hash, content.size, content.mtime);
            dao.objects.update(child.store, childOid, content.version);
            dao.locations.add(childOid, content.version, device);
            transformTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, child.store, childOid, TransformType.UPDATE_CONTENT, content.version, null, null, null, null);
        }

        LOGGER.info("insert {} into {}", childOid, parentOid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private Updated renameChild(DAO dao, DID device, UniqueID parentOid, UniqueID childOid, byte[] newChildName) throws NotFoundException, NameConflictException {
        // get the parent
        LogicalObject parent = getParent(dao, parentOid);

        // get the current child name (informational)
        byte[] currentChildName = dao.children.getActiveChildName(parentOid, childOid);
        if (Arrays.equals(newChildName, currentChildName)) {
            long matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(parent.store, parent.oid, childOid);
            if (matchingTransform != 0L) {
                LOGGER.info("no-op for renameChild operation on child {} of {}", childOid, parentOid);
                return new Updated(matchingTransform, parent);
            }
        }

        // the child we're removing exists and is not locked
        // strictly for rename operations the parent doesn't need to be unlocked, but to keep child names consistent across a migration don't change the name of a locked child
        getUnlockedObject(dao, childOid);

        // the child we're renaming is the child of this object and not deleted
        Preconditions.checkArgument(dao.children.isActiveChild(parentOid, childOid), "%s is not an active child of %s", childOid, parentOid);

        // check for name conflicts with the new name
        checkForNameConflicts(dao, parentOid, newChildName);

        // rename the child to the new name within the same tree
        long transformTimestamp = renameChild(dao, device, parent, childOid, newChildName);

        LOGGER.info("rename {} in {}", childOid, parentOid);

        // return the latest version of the *parent* object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private List<Updated> moveChild(DAO dao, DID device, UniqueID oldParentOid, UniqueID newParentOid, UniqueID childOid, byte[] childName)
    {
        UniqueID currentParentOid = Identifiers.isMountPoint(childOid) ? dao.mountPoints.getMountPointParent(getStore(dao, oldParentOid), childOid) : dao.children.getParent(childOid);
        // check if this operation would be a no-op
        if (newParentOid.equals(currentParentOid) && Arrays.equals(childName, dao.children.getActiveChildName(newParentOid, childOid))) {
            LogicalObject newParent = getExistingObject(dao, newParentOid);
            long insertTransform = dao.transforms.getLatestMatchingTransformTimestamp(newParent.store, newParentOid, childOid);
            if (insertTransform != 0L) {
                LOGGER.info("no-op for removeChild operation on child {} of {}", childOid, oldParentOid);
                return Lists.newArrayList(new Updated(insertTransform, newParent));
            }
        }

        Preconditions.checkArgument(oldParentOid.equals(currentParentOid) && !dao.children.isDeleted(oldParentOid, childOid), "%s is not an active child of %s", childOid, oldParentOid);
        Preconditions.checkArgument(!isInSubtree(dao, childOid, newParentOid), "cannot complete move op, destination %s is under moving object %s", newParentOid, childOid);

        // get both parents and child, must all be unlocked to keep migration queue consistent
        LogicalObject oldParent = getUnlockedParent(dao, oldParentOid);
        LogicalObject newParent = getUnlockedParent(dao, newParentOid);
        LogicalObject child = getUnlockedObject(dao, childOid);

        Preconditions.checkArgument(isFolder(newParent.objectType), "cannot insert into %s", newParent.objectType);

        // check for name conflicts within the parent
        checkForNameConflicts(dao, newParentOid, childName);

        if(child.objectType == ObjectType.STORE) {
            Preconditions.checkArgument(isInRootStore(dao, newParentOid), "can only move mount points in user root stores");
            // need to remove the db row to remove conflicts
            dao.mountPoints.remove(oldParent.store, childOid);
        }

        Atomic atomic = new Atomic(2);
        List<Updated> updates = Lists.newArrayListWithCapacity(2);

        long transformTimestamp = attachChild(dao, device, newParent, child, childName, null, atomic);
        LOGGER.info("insert {} into {} as part of move", childOid, newParentOid);
        updates.add(new Updated(transformTimestamp, getExistingObject(dao, newParentOid)));

        transformTimestamp = detachChild(dao, device, oldParent, child, null, atomic);
        LOGGER.info("remove {} from {} as part of move", childOid, oldParentOid);
        updates.add(new Updated(transformTimestamp, getExistingObject(dao, oldParentOid)));

        return updates;
    }

    private Updated removeChild(DAO dao, DID device, UniqueID parentOid, UniqueID childOid, @Nullable UniqueID migrantOid) throws NotFoundException {
        Preconditions.checkArgument(!Identifiers.isRootStore(childOid), "cannot remove root store %s", childOid);

        // get the parent
        LogicalObject parent = getUnlockedParent(dao, parentOid);

        if (dao.children.isDeleted(parentOid, childOid)) {
            long matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(parent.store, parent.oid, childOid);
            if (matchingTransform != 0L) {
                LOGGER.info("no-op for removeChild operation on child {} of {}", childOid, parentOid);
                return new Updated(matchingTransform, parent);
            }
        }

        // the child we're removing exists and isn't locked (can't remove objects that are already in migration queue)
        LogicalObject child = getUnlockedObject(dao, childOid);

        // the child we're removing is actually the child of this object
        Preconditions.checkArgument(dao.children.isActiveChild(parentOid, childOid), "child %s not found under parent %s", childOid, parentOid);

        // detach the child from its parent
        long transformTimestamp = detachChild(dao, device, parent, child, migrantOid, null);

        LOGGER.info("remove {} from {}", childOid, parentOid);

        // return the latest version of the object
        return new Updated(transformTimestamp, getExistingObject(dao, parentOid));
    }

    private Updated migrateObject(DAO dao, DID device, UniqueID parentOid, UniqueID newOid, UniqueID migrantOid, byte[] name)
    {
        LogicalObject migrant = getExistingObject(dao, migrantOid);
        Preconditions.checkArgument(!Identifiers.isRootStore(migrantOid), "can't migrate root store");
        Preconditions.checkArgument(!isInSubtree(dao, migrantOid, parentOid));
        // migrating a shared folder into another store converts it into a folder, so as not to cause nested sharing
        return insertChild(dao, device, parentOid, newOid, migrant.objectType == ObjectType.STORE ? ObjectType.FOLDER : migrant.objectType, name, migrantOid);
    }

    private Updated makeContent(DAO dao, DID device, UniqueID oid, long deviceVersion, byte[] contentHash, long contentSize, long contentTime) throws NotFoundException, VersionConflictException {
        // check that the object exists
        LogicalObject object = getUnlockedObject(dao, oid);

        // check that we're trying to add content for a file
        Preconditions.checkArgument(isFile(object.objectType), "cannot add content for %s type", object.objectType);

        // check if the new content matches the object's latest content (disregarding version and mtime)
        Content currentObjectContent = dao.objectProperties.getLatest(oid);
        if (currentObjectContent != null && currentObjectContent.equals(new Content(oid, object.version, contentHash, contentSize, currentObjectContent.mtime))) {
            long matchingTransform = dao.transforms.getLatestMatchingTransformTimestamp(object.store, oid);
            if (matchingTransform != 0L) {
                LOGGER.info("no-op for makeContent operation on {}", oid);
                return new Updated(matchingTransform, object);
            }
        }

        // cannot update deleted files
        UniqueID parent = dao.children.getParent(oid);
        Preconditions.checkState(parent != null, "could not find parent of file %s", oid);
        Preconditions.checkArgument(!dao.children.isDeleted(parent, oid), "cannot updated deleted file %s", oid);

        // check that we're at the right version
        if (deviceVersion != object.version) {
            throw new VersionConflictException(oid, deviceVersion, object.version);
        }

        // create an entry for a new version of the content
        long transformTimestamp = newContent(dao, device, object, contentHash, contentSize, contentTime);

        // return the latest version of the object
        return new Updated(transformTimestamp, getExistingObject(dao, oid));
    }

    private SID changeToStore(DAO dao, UniqueID oid, Share op) {
        UniqueID parent;
        OID folderOID;
        if (op.child == null) {
            // legacy SHARE API
            parent= dao.children.getParent(oid);
            folderOID = new OID(oid);
        } else {
            parent = dao.children.getParent(op.child);
            Preconditions.checkArgument(oid.equals(parent), "SHARE operation submitted wrong parent %s of anchor %s", oid, op.child);
            folderOID = new OID(op.child);
        }

        // check that object exists
        LogicalObject folder = getUnlockedObject(dao, folderOID);

        // can only migrate normal folders in a user root
        // N.B. can't check the logicalObject's store value because we might be under another folder that just got migrated, and the changes haven't been propagated to this folder yet
        Preconditions.checkArgument(isInRootStore(dao, folderOID) && folder.objectType == ObjectType.FOLDER, "oid %s must be a folder in a user's root store", folderOID);

        // cannot share a folder that contains shared folders
        Preconditions.checkArgument(!containsSharedFolder(dao, folder.store, folderOID), "cannot share oid %s because it contains a shared folder", folderOID);

        Preconditions.checkState(parent != null, "folder to be migrated %s does not have a parent", folderOID);
        Preconditions.checkArgument(!dao.children.isDeleted(parent, folderOID), "cannot migrate deleted folder %s", folderOID);
        byte[] folderName = dao.children.getChildName(parent, folderOID);
        Preconditions.checkState(folderName != null, "folder to be migrated %s does not have a name", folderOID);

        // create the mountpoint entry and lock the migrating folder
        SID store = SID.folderOID2convertedStoreSID(folderOID);
        convertToAnchor(dao, folderOID, folder.store, parent, folderName);
        newStore(dao, store);
        return store;
    }

    private LockableLogicalObject getParent(DAO dao, UniqueID oid) throws NotFoundException {
        // check if the parent exists
        LockableLogicalObject parent = dao.objects.get(oid);
        // if it doesn't exist, and it's a shared folder, create it
        if (parent == null) {
            if (Identifiers.isRootStore(oid) || Identifiers.isMountPoint(oid)) {
                parent = newStore(dao, oid);
            } else {
                throw new NotFoundException(oid);
            }
        }

        return parent;
    }

    private void checkForNameConflicts(DAO dao, UniqueID parentOid, byte[] childName) throws NotFoundException, NameConflictException {
        UniqueID childOid = dao.children.getActiveChildNamed(parentOid, childName);
        if (childOid != null) {
            throw new NameConflictException(parentOid, childName, childOid);
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

    private boolean isInRootStore(DAO dao, UniqueID oid) {
        if (getExistingObject(dao, oid).locked != LockStatus.UNLOCKED) {
            // encountering a locked object means we can't be sure of which store it will actually be in, as it could be part of an active migration
            // N.B. this also means we don't allow sharing a folder that's part of an active migration (cross-store move)
            return false;
        } else if (Identifiers.isRootStore(oid) || Identifiers.isMountPoint(oid)) {
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

    private static long attachChild(DAO dao, DID device, LogicalObject parent, LogicalObject child, byte[] childName, @Nullable UniqueID migrant, @Nullable Atomic atomic) {
        long newParentVersion = parent.version + 1;

        // add entry in transforms table and update our latest known max logical timestamp
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, parent.store, parent.oid, TransformType.INSERT_CHILD, newParentVersion, child.oid, childName, migrant, atomic);

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

    private static long detachChild(DAO dao, DID device, LogicalObject parent, LogicalObject child, @Nullable UniqueID migrantOid, @Nullable Atomic atomic) {
        long newParentVersion = parent.version + 1;

        // add entry in transforms table and update our latest known max logical timestamp
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, parent.store, parent.oid, TransformType.REMOVE_CHILD, newParentVersion, child.oid, null, migrantOid, atomic);

        // update the version of the parent
        dao.objects.update(parent.store, parent.oid, newParentVersion);

        // remove the entry for the child or set it to deleted if this is the last reference (mount points don't get deleted history)
        if (child.objectType != ObjectType.STORE && dao.children.getActiveReferenceCount(child.oid) == 1) {
            dao.children.setDeleted(child.oid, true);
        } else {
            dao.children.remove(parent.oid, child.oid);
        }

        // update mountpoints table if the removed child was a mount point, but not if part of a Move operation (indicated by atomic)
        if (child.objectType == ObjectType.STORE && atomic == null) {
            dao.mountPoints.remove(parent.store, child.oid);
        }

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private long renameChild(DAO dao, DID device, LogicalObject parent, UniqueID childOid, byte[] childName) {
        long newParentVersion = parent.version + 1;

        // add entry in transforms table and update our latest known max logical timestamp
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, parent.store, parent.oid, TransformType.RENAME_CHILD, newParentVersion, childOid, childName, null, null);

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
        long logicalTimestamp = addTransformAndUpdateMaxLogicalTimestamp(dao, device, file.store, file.oid, TransformType.UPDATE_CONTENT, newVersion, null, null, null, null);

        // add a row to the content table
        dao.objectProperties.add(file.oid, newVersion, hash, size, mtime);

        // update the version for the object
        dao.objects.update(file.store, file.oid, newVersion);

        // add the location of the new content
        dao.locations.add(file.oid, newVersion, device);

        // return the timestamp at which the transform was made
        return logicalTimestamp;
    }

    private static void convertToAnchor(DAO dao, OID folder, UniqueID store, UniqueID parent, byte[] folderName) {
        // cannot modify a folder after sharing it
        dao.objects.setLocked(folder, LockStatus.LOCKED);
        dao.children.remove(parent, folder);

        OID anchor = SID.folderOID2convertedAnchorOID(folder);
        dao.children.add(parent, anchor, folderName);
        dao.mountPoints.add(store, anchor, parent);
    }

    private static long addTransformAndUpdateMaxLogicalTimestamp(DAO dao, DID device, UniqueID store, UniqueID oid, TransformType transformType, long newVersion, @Nullable UniqueID child, @Nullable byte[] name, @Nullable UniqueID migrant, @Nullable Atomic atomic) {
        // add an entry in the transforms table
        long timestamp = System.currentTimeMillis();
        long logicalTimestamp = dao.transforms.add(device, store, oid, transformType, newVersion, child, name, migrant, timestamp, atomic);

        // update the store max timestamp
        dao.logicalTimestamps.updateLatest(store, logicalTimestamp);

        // return logical timestamp associated with this transform
        return logicalTimestamp;
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
     * @param operationType {@link LocationUpdateType#INSERT} to add {@code did} to
     *                   the list of devices or {@link LocationUpdateType#REMOVE}
     *                   to remove {@code did} from the list of devices
     * @param oid object for which the location list should be updated
     * @param version integer version > 0 of {@code oid}
     * @param did device id to be added or removed from the location list
     * @throws NotFoundException if the {@code object} for which the location list should be updated does not exist
     * @throws AccessException if {@code user} cannot update the list of locations for {@code oid}, {@code version}
     */
    public void performLocationUpdate(UserID user, LocationUpdateType operationType, UniqueID oid, long version, DID did) throws NotFoundException, AccessException {
        AccessToken accessToken = checkAccess(user, oid, Access.READ, Access.WRITE);
        inTransaction(dao -> {
            performLocationUpdate(dao, accessToken, operationType, oid, version, did);
            return null;
        });
    }

    private void performLocationUpdate(DAO dao, AccessToken accessToken, LocationUpdateType operationType, UniqueID oid, long version, DID did) throws NotFoundException, AccessException {
        checkAccessGranted(dao, accessToken, oid, Access.READ, Access.WRITE);

        switch (operationType) {
            case INSERT:
                insertLocation(dao, oid, version, did);
                break;
            case REMOVE:
                removeLocation(dao, oid, version, did);
                break;
            default:
                throw new IllegalArgumentException("unhandled location update type " + operationType.name());
        }
    }

    private void insertLocation(DAO dao, UniqueID oid, long version, DID did) throws NotFoundException {
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

    private void removeLocation(DAO dao, UniqueID oid, long version, DID did) throws NotFoundException {
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

    private LockableLogicalObject getExistingObject(DAO dao, UniqueID oid) throws NotFoundException {
        LockableLogicalObject object = dao.objects.get(oid);

        if (object == null) {
            throw new NotFoundException(oid);
        }

        return object;
    }

    public boolean doesExist(DAO dao, UniqueID oid)
    {
        return dao.objects.get(oid) != null;
    }

    private LockableLogicalObject getUnlockedParent(DAO dao, UniqueID oid) throws ObjectLockedException {
        LockableLogicalObject parent = getParent(dao, oid);
        if (parent.locked != LockStatus.UNLOCKED) {
            throw new ObjectLockedException(oid);
        }
        return parent;
    }

    private LockableLogicalObject getUnlockedObject(DAO dao, UniqueID oid)
            throws ObjectLockedException
    {
        LockableLogicalObject object = getExistingObject(dao, oid);
        if (object.locked != LockStatus.UNLOCKED) {
            throw new ObjectLockedException(oid);
        }
        return object;
    }

    public boolean isFolder(ObjectType objectType) {
        return objectType == ObjectType.STORE || objectType == ObjectType.FOLDER;
    }

    public boolean isFile(ObjectType objectType) {
        return objectType == ObjectType.FILE;
    }

    private static void checkVersionInRange(LogicalObject object, long version) {
        Preconditions.checkArgument(version >= 0, "version %s less than 0", version);
        Preconditions.checkArgument(version <= object.version, "version %s exceeds upper bound of %s", version, object.version);
    }

    private static void verifyStore(UniqueID oid) {
        Preconditions.checkArgument(Identifiers.isRootStore(oid) || Identifiers.isSharedFolder(oid), "%s not an sid", oid);
    }

    public List<Child> children(DAO dao, UniqueID oid)
    {
        List<Child> children = Lists.newArrayList();
        try (ResultIterator<DeletableChild> c = dao.children.getChildren(oid)) {
            while(c.hasNext()) {
                DeletableChild child = c.next();
                if (!child.deleted) {
                    children.add(child);
                }
            }
            return children;
        }
    }
}
