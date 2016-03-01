package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.ids.*;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.transform.TransformBatchResult;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.dao.*;
import com.aerofs.polaris.dao.types.LockableLogicalObject;
import com.aerofs.polaris.logical.*;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RolesAllowed(Roles.USER)
@Path("/conversion")
@Singleton
public class ConversionResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformBatchResource.class);
    private final ObjectStore objectStore;
    private final DBI dbi;
    private final Cache<OIDAndComponent, List<Conversion.Tick>> distVersionCache = CacheBuilder.newBuilder().softValues().maximumSize(1024*128).build();

    public ConversionResource(@Context DBI dbi, @Context ObjectStore objects, @Context Notifier notifier) {
        this.objectStore = objects;
        this.dbi = dbi;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/store/{sid}")
    public TransformBatchResult submitBatch(@Context AeroUserDevicePrincipal principal, @PathParam("sid") UniqueID store, TransformBatch batch) {
        List<TransformBatchOperationResult> results = Lists.newArrayListWithCapacity(batch.operations.size());
        LOGGER.debug("received conversion operations {}", batch);

        for (TransformBatchOperation operation: batch.operations) {
            try {
                results.add(new TransformBatchOperationResult(performConversionTransform(principal, store, operation)));
            } catch (Exception e) {
                Throwable cause = Resources.rootCause(e);
                TransformBatchOperationResult result = new TransformBatchOperationResult(Resources.getBatchErrorFromThrowable(cause));
                if (cause instanceof PolarisException || cause instanceof IllegalArgumentException) {
                    LOGGER.info("fail conversion batch operation {}", operation, cause);
                } else {
                    LOGGER.warn("unexpected fail conversion batch operation {}", operation, cause);
                }
                results.add(result);
                break; // abort early if a batch operation fails
            }
        }
        return new TransformBatchResult(results);
    }

    private OperationResult performConversionTransform(AeroUserDevicePrincipal principal, UniqueID store, TransformBatchOperation operation)
    {
        List<UniqueID> affectedObjects = Lists.newArrayList();
        boolean aliased = dbi.inTransaction((conn, status) -> {
            Conversion conversion = conn.attach(Conversion.class);
            UniqueID target = conversion.getAliasNullable(operation.oid, store);
            // if assuming acting purely on other objects from conversion, target should always be non-null here
            affectedObjects.add(target != null ? target : operation.oid);
            boolean a = target != null;

            // this never does anything, since INSERT_CHILD and UPDATE_CONTENT have affectedOIDs -> empty list
            for (UniqueID oid : operation.operation.affectedOIDs()) {
                target = conversion.getAliasNullable(oid, store);
                affectedObjects.add(target != null ? target : oid);
            }
            return a;
        });

        ObjectStore.AccessToken accessToken = objectStore.checkAccess(principal.getUser(), affectedObjects, Access.READ, Access.WRITE);
        Preconditions.checkArgument(accessToken.stores.contains(store) || aliased, "operations submitted to route for store %s does not operate on that store", store);
        Lock l = objectStore.lockObject(affectedObjects.get(0));
        try {
            return performTransformIfVersionDominates(principal, accessToken, store, affectedObjects.get(0), operation.operation);
        } finally {
            l.unlock();
        }
    }

    private OperationResult performTransformIfVersionDominates(AeroUserDevicePrincipal principal, ObjectStore.AccessToken token, UniqueID store, UniqueID oid, Operation operation)
    {
        Preconditions.checkArgument(operation.type == OperationType.INSERT_CHILD || operation.type == OperationType.UPDATE_CONTENT, "conversion only accepts insert and update operations");

        return dbi.inTransaction((conn, status) -> {
            Conversion conversion = conn.attach(Conversion.class);
            DAO dao = new DAO(conn);
            OperationResult result = null;
            switch (operation.type) {
                case INSERT_CHILD: {
                    InsertChild ic = (InsertChild) operation;
                    Preconditions.checkArgument(ic.versions != null, "conversion operations must include distributed versions");
                    Preconditions.checkArgument(ic.aliases != null, "conversion operations must include aliases");

                    UniqueID child = mergeMultipleResolutions(dao, conversion, token, principal.getDevice(), store, resolveChild(dao, conversion, store, ic));
                    List<Conversion.Tick> currentVersion = getDistributedVersion(child, Conversion.COMPONENT_META, conversion);
                    UniqueID currentParent = Identifiers.isSharedFolder(child) ? dao.mountPoints.getMountPointParent(store, child) : dao.children.getParent(child);
                    UniqueID folderToShare = null;
                    // first clause is for multiple users inserting a shared folder
                    if ((Identifiers.isSharedFolder(child) && currentParent == null) || newVersionDominates(currentVersion, ic.versions)) {
                        if (Identifiers.isSharedFolder(child) && currentParent == null)  {
                            LOGGER.info("inserting anchor {} into store {}", child, store);
                            UniqueID folder = SID.convertedStoreSID2folderOID(new SID(child));
                            UniqueID folderAlias = conversion.getAliasNullable(folder, store);
                            if (folderAlias == null) {
                                // folder doesn't exist yet, need to create aliases for the original folder
                                conversion.addAlias(folder, store, child);
                            } else if (!folderAlias.equals(store)){
                                folderToShare = child;
                                // repurpose the op to move the pre-sharing folder to the right spot and name
                                ic = new InsertChild(folderAlias, dao.objectTypes.get(folderAlias), ic.childName, null,
                                        toMap(getDistributedVersion(folderAlias, Conversion.COMPONENT_META, conversion)), Lists.newArrayList());
                                child = folderAlias;
                                currentParent = dao.children.getParent(child);
                            }
                        }

                        if (currentParent != null && (dao.children.isDeleted(currentParent, child) || dao.objects.get(child).locked == LockStatus.MIGRATED)) {
                            if (folderToShare != null && folderToShare.equals(SID.folderOID2convertedStoreSID(new OID(child)))) {
                                LOGGER.info("restoring child {} as anchor to be shared", child);
                                //restore a deleted anchor if necessary so we can share it after
                                byte [] oldName = dao.children.getChildName(currentParent, child);
                                UniqueID conflict = dao.children.getActiveChildNamed(currentParent, oldName);
                                if (notNullAndNotEqual(conflict, child)) {
                                    // move a conflict out of the way of the restore
                                    objectStore.performTransform(dao, token, principal.getDevice(), currentParent, new MoveChild(conflict, currentParent, nonConflictingName(dao, currentParent, conflict, oldName)));
                                }
                                objectStore.performTransform(dao, token, principal.getDevice(), child, new Restore());
                                performInsert(dao, conversion, token, principal, oid, currentParent, child, ic);
                            } else {
                                LOGGER.info("discarding op {} on deleted/migrated {}", ic, child);
                            }
                        } else {
                            result = performInsert(dao, conversion, token, principal, oid, currentParent, child, ic);
                        }
                        saveVersion(conversion, child, Conversion.COMPONENT_META, ic.versions);
                    }
                    // N.B. even if the version doesn't dominate, could still have additional aliases to add to the db, and addAlias no-ops on duplicate key
                    for (UniqueID alias : ic.aliases) {
                        conversion.addAlias(alias, store, child);
                    }
                    // make sure that all existing objects have an alias, even if it just points to themselves
                    if (conversion.getAliasNullable(ic.child, store) == null) {
                        conversion.addAlias(ic.child, store, child);
                    }

                    if (folderToShare != null) {
                        for (TransformBatchOperation op : shareFolder(dao, conversion, store, child, folderToShare, oid, ic.childName)) {
                            result = objectStore.performTransform(dao, token, principal.getDevice(), op.oid, op.operation);
                        }
                    }
                    break;
                }
                case UPDATE_CONTENT: {
                    UpdateContent uc = (UpdateContent) operation;
                    Preconditions.checkArgument(uc.versions != null, "conversion operations must include distributed versions");
                    List<Conversion.Tick> currentVersion = getDistributedVersion(oid, Conversion.COMPONENT_CONTENT, conversion);
                    if (newVersionDominates(currentVersion, uc.versions)) {
                        LockableLogicalObject o = dao.objects.get(oid);
                        Preconditions.checkState(o != null, "cannot find object %s to update", oid);
                        UniqueID parent = dao.children.getParent(oid);
                        Preconditions.checkState(parent != null, "cannot find parent of object %s to update", oid);
                        if (o.locked == LockStatus.MIGRATED || dao.children.isDeleted(parent, oid)) {
                            LOGGER.info("discarding op {} on deleted/migrated {}");
                        } else {
                            LOGGER.debug("dominating op {} on oid {}", uc, oid);
                            // make sure there's no version conflict arising from the update content
                            result = objectStore.performTransform(dao, token, principal.getDevice(), oid, new UpdateContent(o.version, uc.hash, uc.size, uc.mtime, null));
                        }
                        saveVersion(conversion, oid, Conversion.COMPONENT_CONTENT, uc.versions);
                    }
                    break;
                }
                default: {
                    throw new Exception("unsupported conversion operation type");
                }
            }

            if (result == null) {
                return new OperationResult(Lists.newArrayList(new Updated(dao.transforms.getLatestLogicalTimestamp(), dao.objects.get(oid))));
            } else {
                return result;
            }
        });
    }

    @Nullable private OperationResult performInsert(DAO dao, Conversion conversion, ObjectStore.AccessToken token, AeroUserDevicePrincipal principal, UniqueID oid, @Nullable UniqueID currentParent, UniqueID child, InsertChild ic)
    {
        LOGGER.debug("dominating op {} on child {} under {}", ic, child, oid);
        Operation op;
        UniqueID conflict = dao.children.getActiveChildNamed(oid, ic.childName);
        if (conflict != null) {
            Preconditions.checkState(conflict.equals(child) || !ic.aliases.contains(conflict), "incorrectly resolved aliases leading to avoidable conflict with %s and %s", child, conflict);
            op = makeOperationWithConflict(dao, conversion, token, principal.getDevice(), oid, currentParent, child, conflict, ic);
        } else {
            op = makeOperation(oid, currentParent, child, ic);
        }

        if (op != null) {
            LOGGER.debug("performing op {} on {}", op, currentParent != null ? currentParent : oid);
            return objectStore.performTransform(dao, token, principal.getDevice(), currentParent != null ? currentParent : oid, op);
        } else {
            return null;
        }
    }

    private List<TransformBatchOperation> shareFolder(DAO dao, Conversion conversion, UniqueID rootStore, UniqueID folder, UniqueID store, UniqueID destination, byte[] name)
    {
        Preconditions.checkState(Identifiers.isRootStore(rootStore), "sharing folder %s not under non-root store %s", folder, rootStore);
        List<TransformBatchOperation> ops = Lists.newArrayList();
        UniqueID parent = dao.children.getParent(folder);
        Preconditions.checkState(parent != null, "found an alias target %s without a parent", folder);

        if (folder.equals(SID.convertedStoreSID2folderOID(new SID(store)))) {
            Preconditions.checkState(parent.equals(destination), "folder to be shared %s not at right destination");
            // we make the SHARE operation an exception here, in that it will restore a deleted object if necessary
            ops.add(new TransformBatchOperation(parent, new Share(folder)));
            conversion.remapAlias(rootStore, folder, store);
        } else {
            // messy folder to shared folder aliasing, delete the mismatch and start the folder from scratch
            ops.add(new TransformBatchOperation(parent, new RemoveChild(new OID(folder))));
            ops.add(new TransformBatchOperation(destination, new InsertChild(store, ObjectType.STORE, name, null, null, null)));
            // don't remap the alias here, because we don't want the shared folder to be affected by further changes to folder
        }
        return ops;
    }

    private Set<UniqueID> resolveChild(DAO dao, Conversion conversion, UniqueID store, InsertChild ic)
    {
        assert ic.aliases != null;
        Set<UniqueID> resolutions = Sets.newHashSet();

        if (Identifiers.isSharedFolder(ic.child)) {
            // Shared Folders cannot have any aliases
            Preconditions.checkArgument(ic.aliases.isEmpty(), "shared folder should not have any aliases");
            resolutions.add(ic.child);
            return resolutions;
        }

        // a couple things could happen here:
        // child might already exist as an alias, have an OID conflict because of a cross-store move, or not have any conflicts
        // some of the submitted aliases might already exist in the same store, or be themselves aliased, in which case we'd like to use one of their OIDs
        // unfortunately, since aliases known are not globally consistent, a later operation could contain more alias information
        // which means that we have to merge any number of already existing objects
        // check if any of the submitted aliases match something already on polaris
        for (UniqueID distributedAlias : ic.aliases) {
            UniqueID aliasForAlias = conversion.getAliasNullable(distributedAlias, store);
            if (aliasForAlias != null) {
                // any existing aliases are treated as canon
                LOGGER.debug("adding target {} of an alias {} as a resolution for {}", aliasForAlias, distributedAlias, ic.child);
                resolutions.add(aliasForAlias);
            }
        }

        if (resolutions.isEmpty() && notNullAndNotEqual(dao.objects.getStore(ic.child), store)) {
            OID newOid = OID.generate();
            LOGGER.info("adding a newly generated OID {} for OID conflict {}", newOid, ic.child);
            resolutions.add(newOid);
        } else {
            UniqueID childAlias = conversion.getAliasNullable(ic.child, store);
            if (childAlias != null) {
                LOGGER.debug("adding existing object/alias {} as resolution for {}", childAlias, ic.child);
                resolutions.add(childAlias);
            } else if (resolutions.isEmpty()) {
                LOGGER.debug("no resolutions available for {}, adding self", ic.child);
                resolutions.add(ic.child);
            }
        }
        return resolutions;
    }

    public UniqueID mergeMultipleResolutions(DAO dao, Conversion conversion, ObjectStore.AccessToken token, DID device, UniqueID store, Set<UniqueID> resolutions)
    {
        Preconditions.checkArgument(resolutions.size() > 0, "cannot merge empty list of resolutions");
        if (resolutions.size() == 1) {
            return resolutions.iterator().next();
        }
        // use the same method the daemon does to choose the target
        UniqueID target = Collections.max(resolutions);
        assert target != null;
        LOGGER.info("merging resolutions {} in store {} into target {}", resolutions, store, target);
        Preconditions.checkState(resolutions.remove(target), "found a highest resolution %s not in list", target);

        for (UniqueID r : resolutions) {
            Preconditions.checkState(!Identifiers.isSharedFolder(r), "shared folders cannot have aliases");
            UniqueID rParent = dao.children.getParent(r);
            if (rParent != null && dao.children.getActiveChildName(rParent, r) != null) {
                LOGGER.info("removing object {} to merge into {}", r, target);
                objectStore.performTransform(dao, token, device, rParent, new RemoveChild(new OID(r)));
            }
            // it is possible to try and merge conflicted subtrees, but for right now it's only introducing extra complication and not considered worthwhile
            // see earlier commits on this file for a first pass impl
            conversion.remapAlias(store, r, target);
        }
        return target;
    }

    private Operation makeOperation(UniqueID oid, @Nullable UniqueID currentParent, UniqueID child, InsertChild ic)
    {
        if (currentParent != null) {
            return new MoveChild(child, oid, ic.childName);
        } else {
            return new InsertChild(child, ic.childObjectType, ic.childName, null, null, null);
        }
    }

    // returns if the operation should be executed or not, will move a name conflicting object out of the way if necessary
    @Nullable private Operation makeOperationWithConflict(DAO dao, Conversion conversion, ObjectStore.AccessToken token, DID device, UniqueID parent, @Nullable UniqueID currentParent, UniqueID child, UniqueID conflict, InsertChild ic)
    {
        assert ic.versions != null;
        if (!conflict.equals(child)) {
            // N.B. cannot just drop operations making a folder because there could be further operations under that folder which will persistently fail
            // instead, we move them into the tree - meaning one of the objects gets renamed
            if (newVersionDominates(getDistributedVersion(conflict, Conversion.COMPONENT_META, conversion), ic.versions)) {
                LOGGER.info("moving name conflict {} with new object {} from under parent {}", conflict, child, parent);
                objectStore.performTransform(dao, token, device, parent, new MoveChild(conflict, parent, nonConflictingName(dao, parent, conflict, ic.childName)));
                return makeOperation(parent, currentParent, child, ic);
            } else {
                if (currentParent != null) {
                    return new MoveChild(child, parent, nonConflictingName(dao, parent, child, ic.childName));
                } else {
                    return new InsertChild(child, ic.childObjectType, nonConflictingName(dao, parent, child, ic.childName), null, null, null);
                }
            }
        } else {
            // inserting a child where it already is with the same name, would be a no-op
            return null;
        }
    }

    private boolean newVersionDominates(List<Conversion.Tick> oldVersion, Map<DID, Long> newVersion)
    {
        for (Conversion.Tick t : oldVersion) {
            if (t.l > newVersion.getOrDefault(t.did, 0L)) {
                return false;
            }
        }
        return true;
    }

    private boolean newVersionDominates(Map<DID, Long> oldVersion, Map<DID, Long> newVersion)
    {
        for (Map.Entry<DID, Long> e : oldVersion.entrySet()) {
            if (newVersion.getOrDefault(e.getKey(), 0L) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    private Map<DID, Long> toMap(List<Conversion.Tick> v)
    {
        return v.stream().collect(Collectors.toMap(x -> x.did, x -> x.l));
    }

    private void saveVersion(Conversion conversion, UniqueID oid, int component, Map<DID, Long> version)
    {
        List<DID> devices = Lists.newArrayList();
        List<Long> versions = Lists.newArrayList();
        List<Conversion.Tick> ticks = Lists.newArrayList();
        version.forEach((key, value) -> {
            devices.add(key);
            versions.add(value);
            ticks.add(new Conversion.Tick(key, value));
        });
        conversion.insertTick(oid, component, devices, versions);
        OIDAndComponent k = new OIDAndComponent(oid, component);
        distVersionCache.put(k, ticks);
    }

    private static boolean notNullAndNotEqual(@Nullable Object match, Object target)
    {
        return match != null && !match.equals(target);
    }

    // FIXME (RD) this method is taken from com.aerofs.lib.Util.java
    // since the scope of usage was small, i didn't think it was worth splitting lib into a separate module - clean up the dependency if this method is used more
    private static final Pattern NEXT_NAME_PATTERN =
            Pattern.compile("(.*)\\(([0-9]+)\\)$");

    private byte[] nonConflictingName(DAO dao, UniqueID parent, UniqueID child, byte[] filename)
    {
        Charset UTF8 = Charset.forName("UTF-8");
        String fn = new String(filename, UTF8);
        int dot = fn.lastIndexOf(".");
        String extension = dot <= 0 ? "" : fn.substring(dot);
        String base = fn.substring(0, fn.length() - extension.length());

        // find the pattern of "(N)" at the end of the main part
        Matcher m = NEXT_NAME_PATTERN.matcher(base);
        String prefix;
        int num;
        if (m.find()) {
            prefix = m.group(1);
            try {
                num = Integer.valueOf(m.group(2)) + 1;
            } catch (NumberFormatException e) {
                // If the number can't be parsed because it's too large, it's probably not us who
                // generated that number. In this case, add a new number after it.
                prefix = base + " ";
                num = 2;
            }
        } else {
            prefix = base + " ";
            num = 2;
        }

        String newName = prefix + '(' + num + ')' + extension;
        UniqueID conflict = dao.children.getActiveChildNamed(parent, newName.getBytes(UTF8));
        while (conflict != null && !conflict.equals(child)) {
            num++;
            newName = prefix + '(' + num + ')' + extension;
            conflict = dao.children.getActiveChildNamed(parent, newName.getBytes(UTF8));
        }
        return newName.getBytes(UTF8);
    }

    private List<Conversion.Tick> getDistributedVersion(UniqueID oid, int component, Conversion conversion) {
        OIDAndComponent key = new OIDAndComponent(oid, component);
        List<Conversion.Tick> cached = distVersionCache.getIfPresent(key);
        if (cached == null) {
            cached = conversion.getDistributedVersion(oid, component);
            distVersionCache.put(key, cached);
        }
        return cached;
    }

    private static class OIDAndComponent {
        final UniqueID oid;
        final int component;

        OIDAndComponent(UniqueID oid, int component) {
            this.oid = oid;
            this.component = component;
        }

        @Override
        public int hashCode() {
            return Objects.hash(oid, component);
        }
    }
}

