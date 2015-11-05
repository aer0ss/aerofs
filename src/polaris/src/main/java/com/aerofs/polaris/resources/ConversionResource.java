package com.aerofs.polaris.resources;

import com.aerofs.auth.server.AeroUserDevicePrincipal;
import com.aerofs.auth.server.Roles;
import com.aerofs.base.BaseLogUtil;
import com.aerofs.ids.*;
import com.aerofs.polaris.PolarisException;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.api.batch.transform.TransformBatch;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperation;
import com.aerofs.polaris.api.batch.transform.TransformBatchOperationResult;
import com.aerofs.polaris.api.batch.transform.TransformBatchResult;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.DeletableChild;
import com.aerofs.polaris.api.types.LogicalObject;
import com.aerofs.polaris.api.types.ObjectType;
import com.aerofs.polaris.dao.*;
import com.aerofs.polaris.logical.*;
import com.aerofs.polaris.notification.Notifier;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.ResultIterator;
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
    // used for no-op replies to conversion operations
    private final LogicalObject DUMMY_OBJECT = new LogicalObject(OID.ROOT, OID.ROOT, 0L, ObjectType.STORE);

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
                OperationResult result = performConversionTransform(principal, store, operation);
                results.add(new TransformBatchOperationResult(result));
            } catch (Exception e) {
                Throwable cause = Resources.rootCause(e);
                TransformBatchOperationResult result = new TransformBatchOperationResult(Resources.getBatchErrorFromThrowable(cause));
                if (cause instanceof PolarisException || cause instanceof IllegalArgumentException) {
                    LOGGER.info("fail conversion batch operation {}", operation, BaseLogUtil.suppress(cause));
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
        OperationResult result = dbi.inTransaction((conn, status) -> {
            Conversion conversion = conn.attach(Conversion.class);
            UniqueID target = conversion.getAliasNullable(operation.oid, store);
            affectedObjects.add(target != null ? target : operation.oid);

            // operation on an object which we are supposed to ignore
            if (OID.TRASH.equals(affectedObjects.get(0))) {
                if (operation.operation instanceof InsertChild) {
                    InsertChild ic = ((InsertChild) operation.operation);
                    conversion.addAlias(ic.child, store, OID.TRASH);
                }
                // exit the operation early, no point trying to operate on objects which don't exist
                return new OperationResult(Lists.newArrayList(new Updated((conn.attach(com.aerofs.polaris.dao.Transforms.class)).getLatestLogicalTimestamp(), DUMMY_OBJECT)));
            }

            // this never does anything, since INSERT_CHILD and UPDATE_CONTENT have affectedOIDs -> empty list
            for (UniqueID oid : operation.operation.affectedOIDs()) {
                target = conversion.getAliasNullable(oid, store);
                affectedObjects.add(target != null ? target : oid);
            }
            return null;
        });

        if (result != null) {
            return  result;
        }

        // TODO since all operations are in the same store, reuse one access token?
        ObjectStore.AccessToken accessToken = objectStore.checkAccess(principal.getUser(), affectedObjects, Access.READ, Access.WRITE);
        Preconditions.checkArgument(accessToken.stores.contains(store), "operations submitted to route for store %s does not operation on that store", store);
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
                // TODO (RD) alias stores with pre-sharing folders
                case INSERT_CHILD: {
                    InsertChild ic = (InsertChild) operation;
                    Preconditions.checkArgument(ic.versions != null, "conversion operations must include distributed versions");
                    Preconditions.checkArgument(ic.aliases != null, "conversion operations must include aliases");
                    UniqueID child = mergeMultipleResolutions(dao, conversion, token, principal.getDevice(), store, resolveChild(dao, conversion, store, ic));
                    List<Conversion.Tick> currentVersion = conversion.getDistributedVersion(child, Conversion.COMPONENT_META);
                    UniqueID currentParent = Identifiers.isSharedFolder(child) ? dao.mountPoints.getMountPointParent(store, child) : dao.children.getParent(child);
                    // first clause is for multiple users inserting a sharedfolder
                    if ((Identifiers.isSharedFolder(child) && currentParent == null) || newVersionDominates(currentVersion, ic.versions)) {
                        LOGGER.debug("dominating op {} on child {} under {}", ic, child, oid);
                        Operation op;
                        UniqueID conflict = dao.children.getActiveChildNamed(oid, ic.childName);
                        if (conflict != null) {
                            Preconditions.checkState(conflict.equals(child) || !ic.aliases.contains(conflict), "incorrectly resolved aliases leading to avoidable conflict with %s and %s", child, conflict);
                            op = moveConflictingObjectIfIncomingVersionDominates(dao, conversion, token, principal.getDevice(), oid, child, conflict, ic) ?
                                    makeOperation(oid, currentParent, child, ic) : null;
                        } else {
                            op = makeOperation(oid, currentParent, child, ic);
                        }

                        if (op != null) {
                            LOGGER.debug("performing op {} on {}", op, currentParent != null ? currentParent : oid);
                            result = objectStore.performTransform(dao, token, principal.getDevice(), currentParent != null ? currentParent : oid, op);
                        }
                        saveVersion(conversion, child, Conversion.COMPONENT_META, ic.versions);
                    }
                    // N.B. even if the version doesn't dominate, could still have additional aliases to add to the db, and addAlias no-ops on duplicate key
                    for (UniqueID alias : ic.aliases) {
                        conversion.addAlias(alias, store, child);
                    }
                    if (!child.equals(ic.child)) {
                        conversion.addAlias(ic.child, store, child);
                    }
                    break;
                }
                case UPDATE_CONTENT: {
                    UpdateContent uc = (UpdateContent) operation;
                    Preconditions.checkArgument(uc.versions != null, "conversion operations must include distributed versions");
                    List<Conversion.Tick> currentVersion = conversion.getDistributedVersion(oid, Conversion.COMPONENT_CONTENT);
                    if (newVersionDominates(currentVersion, uc.versions)) {
                        LOGGER.debug("dominating op {} on oid {}", uc, oid);
                        // make sure there's no version conflict arising from the update content
                        LogicalObject o = dao.objects.get(oid);
                        Preconditions.checkState(o != null, "cannot find object %s to update", oid);
                        result = objectStore.performTransform(dao, token, principal.getDevice(), oid, new UpdateContent(o.version, uc.hash, uc.size, uc.mtime, null));
                        saveVersion(conversion, oid, Conversion.COMPONENT_CONTENT, uc.versions);
                    }
                    break;
                }
                default: {
                    throw new Exception("unsupported conversion operation type");
                }
            }

            if (result == null) {
                return new OperationResult(Lists.newArrayList(new Updated(dao.transforms.getLatestLogicalTimestamp(), DUMMY_OBJECT)));
            } else {
                return result;
            }
        });
    }

    private ArrayList<UniqueID> resolveChild(DAO dao, Conversion conversion, UniqueID store, InsertChild ic)
    {
        assert ic.aliases != null;
        Set<UniqueID> resolutions = Sets.newHashSet();

        if (Identifiers.isSharedFolder(ic.child)) {
            // Shared Folders cannot have any aliases
            Preconditions.checkArgument(ic.aliases.isEmpty(), "shared folder should not have any aliases");
            Preconditions.checkState(conversion.getAliasNullable(ic.child, store) == null, "shared folder already has alias on polaris");
            resolutions.add(ic.child);
            // TODO (RD) detect if non-shared folder exists
            return Lists.newArrayList(resolutions);
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
                LOGGER.debug("adding an aliased-alias {} for an alias {} as a resolution for {}", aliasForAlias, distributedAlias, ic.child);
                resolutions.add(aliasForAlias);
            } else {
                UniqueID aliasStore = dao.objects.getStore(distributedAlias);
                if (aliasStore != null && aliasStore.equals(store)) {
                    LOGGER.debug("adding an existing alias {} as a resolution for {}", distributedAlias, ic.child);
                    resolutions.add(distributedAlias);
                }
            }
        }
        UniqueID resolvedChild = conversion.getAliasNullable(ic.child, store);
        resolvedChild = resolvedChild != null ? resolvedChild : ic.child;
        UniqueID storeForChild = dao.objects.getStore(resolvedChild);
        if (notNullAndNotEqual(storeForChild, store) && resolutions.isEmpty()) {
            // OID conflict with object from other store, need to make new OID to resolve to
            Preconditions.checkState(resolvedChild.equals(ic.child), "hitting OID conflict for target %s of alias %s", resolvedChild, ic.child);
            LOGGER.debug("adding a newly generated OID for OID conflict {}", resolvedChild);
            resolutions.add(OID.generate());
        } else if (storeForChild != null && storeForChild.equals(store)) {
            // meaning this child already exists in the store
            LOGGER.debug("adding existing object/alias {} as resolution for {}", resolvedChild, ic.child);
            resolutions.add(resolvedChild);
        } else if (resolutions.isEmpty()) {
            Preconditions.checkState(resolvedChild.equals(ic.child), "found a nonexistent target %s for alias %s", resolvedChild, ic.child);
            LOGGER.debug("no resolutions available for {}", resolvedChild);
            resolutions.add(resolvedChild);
        }
        return Lists.newArrayList(resolutions);
    }

    public UniqueID mergeMultipleResolutions(DAO dao, Conversion conversion, ObjectStore.AccessToken token, DID device, UniqueID store, ArrayList<UniqueID> resolutions)
    {
        // TODO (RD) this makes transactions unboundedly large, but hopefully this is rare enough that it'll be alright
        Preconditions.checkArgument(resolutions.size() > 0, "cannot merge empty list of resolutions");
        if (resolutions.size() == 1) {
            return resolutions.get(0);
        }
        // TODO (RD) come up with a better algorithm than most dominating, perhaps just highest device tick?
        Map<DID, Long> highestVersion = Maps.newHashMap();
        UniqueID highestResolution = null;
        for (UniqueID r : resolutions) {
            Map<DID, Long> rVersion = toMap(conversion.getDistributedVersion(r, Conversion.COMPONENT_META));
            if (newVersionDominates(highestVersion, rVersion)) {
                highestVersion = rVersion;
                highestResolution = r;
            }
        }
        assert highestResolution != null;
        LOGGER.info("merging resolutions {} in store {} into target {}", resolutions, store, highestResolution);
        Preconditions.checkState(resolutions.remove(highestResolution), "found a highest resolution %s not in list", highestResolution);
        List<DeletableChild> currentChildren = getChildren(dao, highestResolution);

        for (UniqueID r : resolutions) {
            Preconditions.checkState(!Identifiers.isSharedFolder(r), "shared folders cannot have aliases");
            UniqueID rParent = dao.children.getParent(r);
            if (rParent != null && dao.children.getActiveChildName(rParent, r) != null) {
                LOGGER.info("removing object {} to merge into {}", r, highestResolution);
                objectStore.performTransform(dao, token, device, rParent, new RemoveChild(new OID(r)));
            }
            List<DeletableChild> rChildren = getChildren(dao, r);
            for (DeletableChild c : rChildren) {
                if (!c.deleted) {
                    DeletableChild conflict = currentChildren.stream().filter(x -> Arrays.equals(c.name, x.name)).findAny().orElse(null);
                    // TODO (RD) merging will have to recursively check for conflicts
//                    if (conflict != null) {
//                        Map<DID, Long> cVers = toMap(conversion.getDistributedVersion(c.oid, Conversion.COMPONENT_META));
//                        if (newVersionDominates(conversion.getDistributedVersion(conflict.oid, Conversion.COMPONENT_META), cVers)) {
//                            // recursive merge or maybe just do db fiddly hackery

//
//                        }
//                    } else {
                    if (conflict == null) {
                        objectStore.performTransform(dao, token, device, r, new MoveChild(c.oid, highestResolution, c.name));
                        currentChildren.add(c);
                    }
                }
            }
            conversion.remapAlias(store, r, highestResolution);
        }
        return highestResolution;
    }

    private List<DeletableChild> getChildren(DAO dao, UniqueID p) {
        List<DeletableChild> children = Lists.newArrayList();
        try (ResultIterator<DeletableChild> c = dao.children.getChildren(p)) {
            while (c.hasNext()) {
                // include deleted objects here as well
                children.add(c.next());
            }
        }
        return children;
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
    private boolean moveConflictingObjectIfIncomingVersionDominates(DAO dao, Conversion conversion, ObjectStore.AccessToken token, DID device, UniqueID parent, UniqueID child, UniqueID conflict, InsertChild ic)
    {
        assert ic.versions != null;
        if (!conflict.equals(child)) {
            // N.B. cannot just drop operations making a folder because there could be further operations under that folder which will persistently fail
            // alternatively, add a new table for objects which we are choosing to ignore
            if (newVersionDominates(conversion.getDistributedVersion(conflict, Conversion.COMPONENT_META), ic.versions)) {
                LOGGER.info("moving name conflict {} with new object {} from under parent {}", conflict, child, parent);
                objectStore.performTransform(dao, token, device, parent, new MoveChild(conflict, parent, nonConflictingName(dao, parent, conflict, ic.childName)));
                return true;
            } else {
                Preconditions.checkState(ic.child.equals(child), "have an illogical alias %s for name conflicting object %s with conflict %s", child, ic.child, conflict);
                conversion.addAlias(ic.child, dao.objects.getStore(parent), OID.TRASH);
            }
        }
        // inserting a child where it already is with the same name, would be a no-op
        return false;
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
        conversion.deleteTicks(oid, component);
        List<DID> devices = Lists.newArrayList();
        List<Long> ticks = Lists.newArrayList();
        version.forEach((key, value) -> {
            devices.add(key);
            ticks.add(value);
        });
        conversion.insertTick(oid, component, devices, ticks);
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
}

