package com.aerofs.polaris.external_api.metadata;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.auth.server.delegated.AeroDelegatedUserDevicePrincipal;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.*;
import com.aerofs.oauth.Scope;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.api.PolarisUtilities;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.Child;
import com.aerofs.polaris.api.types.Content;
import com.aerofs.polaris.external_api.etag.EntityTagSet;
import com.aerofs.polaris.external_api.rest.util.Version;
import com.aerofs.polaris.logical.*;
import com.aerofs.polaris.notification.Notifier;
import com.aerofs.rest.api.*;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.util.MimeTypeDetector;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.SettableFuture;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.*;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static com.aerofs.polaris.acl.Access.*;
import static com.aerofs.polaris.api.types.ObjectType.FILE;
import static com.aerofs.polaris.api.types.ObjectType.FOLDER;
import static com.aerofs.polaris.external_api.Constants.APPDATA_FOLDER_NAME;
import static com.aerofs.polaris.external_api.Constants.EXTERNAL_API_LOCATION;
import static com.aerofs.polaris.external_api.metadata.RestObjectResolver.fromRestObject;
import static com.aerofs.polaris.external_api.metadata.RestObjectResolver.toRestObject;
import static com.aerofs.polaris.logical.ObjectStore.AccessToken;
import static com.aerofs.rest.api.File.ContentState;
import static com.google.common.base.Preconditions.checkState;
import static javax.ws.rs.core.Response.Status.*;
import static org.skife.jdbi.v2.TransactionIsolationLevel.READ_COMMITTED;

@Singleton
public class MetadataBuilder
{
    private static final Logger l = Loggers.getLogger(MetadataBuilder.class);

    private final MimeTypeDetector detector;
    private final ObjectStore objectStore;
    private final Notifier notifier;
    private final DBI dbi;
    private final FolderSharer fs;
    private final StoreNames sn;

    @Inject
    public MetadataBuilder(@Context ObjectStore objectStore, @Context MimeTypeDetector detector,
            @Context Notifier notifier, @Context DBI dbi, @Context FolderSharer fs,
            @Context StoreNames sn)
    {
        this.objectStore = objectStore;
        this.detector = detector;
        this.notifier = notifier;
        this.dbi = dbi;
        this.fs = fs;
        this.sn = sn;
    }

    private boolean hasUnrestrictedPermission(Map<Scope, Set<RestObject>> scopes, Scope scope)
    {
        return !Scope.isQualifiable(scope) || Collections.<RestObject>emptySet().equals(scopes.get(scope));
    }

    private boolean tokenHasPermission(Map<Scope, Set<RestObject>> scopes, Scope scope)
    {
        Set<RestObject> objects = scopes.get(scope);
        return objects != null && objects.isEmpty();
    }

    private boolean isObjectInTokenScope(AeroOAuthPrincipal principal,
            UniqueID oid, Scope scope, LinkedHashMap<UniqueID, Folder> parentFolders)
    {
        Preconditions.checkArgument(scope == Scope.READ_FILES || scope == Scope.WRITE_FILES,
                "Scope is neither READ or WRITE FILE.");
        Map<Scope, Set<RestObject>> qualifiedScopes = principal.scope();

        // Check if the APPDATA scope is not empty and then check if the object sent in the request
        // is under the .appdata folder. Broken down into two ifs for clarity.
        if (tokenHasPermission(qualifiedScopes, Scope.APPDATA)) {
            List<String> parentNames = parentFolders.values().stream().map(folder -> folder.name)
                    .collect(Collectors.toList());
            if (parentNames.contains(APPDATA_FOLDER_NAME)) return true;
        }
        if (!qualifiedScopes.containsKey(scope)) return false;
        if (hasUnrestrictedPermission(qualifiedScopes, scope)) return true;

        for (RestObject objectInTokenScope : qualifiedScopes.get(scope)) {
            UniqueID tokenOID = fromRestObject(principal, objectInTokenScope);
            if (oid.equals(tokenOID) || parentFolders.keySet().contains(tokenOID)) return true;
        }
        return false;
    }

    private void throwIfInSufficientTokenScope(AeroOAuthPrincipal principal, UniqueID oid, Scope scope,
            LinkedHashMap<UniqueID, Folder> parentFolders)
    {
        if (!isObjectInTokenScope(principal, oid, scope, parentFolders)) {
            throw new WebApplicationException(Response
                    .status(FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(new Error(Error.Type.FORBIDDEN, "Token lacks required scope"))
                    .build());
        }
    }

    private UniqueID createAppDataIfNecessary(DAO dao, AeroOAuthPrincipal principal, SID rootSID,
            List<Updated> updated)
    {
        UniqueID appDataId = dao.children.getActiveChildNamed(rootSID, PolarisUtilities.stringToUTF8Bytes(APPDATA_FOLDER_NAME));
        if (appDataId == null) {
            // Create .appdata
            appDataId = OID.generate();
            // N.B. Any checkAccess call made in this function will not require a network call to
            // Sparta because the store for both the appdata folder and the {client_id} folder
            // is the root folder of the user, for which we don't have to check with Sparta. We
            // still call this method to get the access token for performTransform.
            AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(),
                    Sets.newHashSet(objectStore.getStore(dao, rootSID)), READ, WRITE);
            updated.addAll(objectStore.performTransform(dao, accessToken,
                    principal.getDID(), rootSID,
                    new InsertChild(appDataId, FOLDER, APPDATA_FOLDER_NAME, null)).updated);
        }

        UniqueID clientId = dao.children.getActiveChildNamed(appDataId, PolarisUtilities.stringToUTF8Bytes(principal.audience()));
        if (clientId == null) {
            // Create .appdata/{clientId}
            // N.B.: Hack to get oid of the created child back to the callee of this method. We want to
            // avoid notifying updated stores in a DB trans, which necessitates need to send
            // back/accumulate list of updated stores. However, we also want the oid of the
            // .appdata/{client id} folder if it exists or if we had to created it.
            clientId = OID.generate();
            AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(),
                    Sets.newHashSet(objectStore.getStore(dao, appDataId)), READ, WRITE);
            updated.addAll(objectStore.performTransform(dao, accessToken,
                    principal.getDID(), appDataId,
                    new InsertChild(clientId, FOLDER, principal.audience(), null)).updated);
            return clientId;
        }
        return clientId;
    }

    private AccessToken checkAccess(AeroOAuthPrincipal principal, List<UniqueID> oids,
            Access... requestedAccess)
    {
        Set<Access> access = Sets.newHashSet(requestedAccess);
        if (tokenHasPermission(principal.scope(), Scope.LINKSHARE)) {
            access.add(MANAGE);
        }
        return objectStore.checkAccess(principal.getUser(), oids,
                access.toArray(new Access[access.size()]));
    }

    private UniqueID restObject2OID(AeroOAuthPrincipal principal, RestObject object)
    {
        UniqueID oid;
        if (object.isAppData()) {
            List<Updated> updated = Lists.newArrayList();
            SID rootSID = SID.rootSID(principal.getUser());
            Lock lock = objectStore.lockObject(rootSID);
            try {
                oid = dbi.inTransaction((conn, status) ->
                        createAppDataIfNecessary(new DAO(conn), principal, rootSID, updated));
                Map<UniqueID, Long> updatedStores = updated.stream()
                        .collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
                updatedStores.forEach(notifier::notifyStoreUpdated);
            } finally {
                lock.unlock();
            }
        } else {
            oid = fromRestObject(principal, object);
        }
        return oid;
    }

    private @Nullable UniqueID getParentOID(DAO dao, UserID user, UniqueID oid)
    {
        if (Identifiers.isRootStore(oid)) {
            return oid;
        }
        // For shared folders look into mount points table because a shared folder might exist
        // under different parents for different users.
        if (Identifiers.isSharedFolder(oid)) {
            return dao.mountPoints.getMountPointParent(SID.rootSID(user), oid);
        } else {
            return dao.children.getParent(oid);
        }
    }

    // Check if the store obtained from the RestObject is same as the store of the object in
    // Polaris DB.
    private boolean isStoreConsistent(AeroOAuthPrincipal principal, DAO dao, UniqueID oid,
            RestObject object)
    {
        UniqueID store = objectStore.getStore(dao, oid);
        UniqueID storeFromRestObject;
        if (object.isRoot() || object.isAppData()) {
            storeFromRestObject = SID.rootSID(principal.getUser());
        } else if (object.getOID().isRoot() || !Identifiers.isSharedFolder(object.getOID())) {
            storeFromRestObject = object.getSID();
        } else {
            storeFromRestObject = object.getOID();
        }
        return store != null && store.equals(storeFromRestObject);
    }

    private Folder folder(String id, String name, String parent, ParentPath path, String sid,
            ChildrenList childrenList)
    {
        return new Folder(id, name, parent, path, sid, childrenList);
    }

    private File file(String id, String name, String parent, ParentPath path, Date mtime, Long size,
            String mimeType, String etag, ContentState contentState)
    {
        return new File(id, name, parent, path, mtime, size, mimeType, etag, contentState);
    }

    private @Nullable String getContentEtag(Content fp)
    {
        return fp == null ? null : new EntityTag(BaseUtil.hexEncode(fp.hash)).getValue();
    }

    private @Nullable Long getSize(Content fp)
    {
        return fp == null ? null : fp.size;
    }

    private @Nullable Date getDate(Content fp)
    {
        return fp == null ? null : new Date(fp.mtime);
    }

    private @Nullable String oid2Sid(UniqueID oid)
    {
        return Identifiers.isSharedFolder(oid)
                ? SID.anchorOID2storeSID(new OID(oid)).toStringFormal()
                : null;
    }

    private boolean isFieldPresent(String [] fields, String field)
    {
        return fields != null && Arrays.asList(fields).contains(field);
    }

    private String resolveName(DAO dao, UniqueID parent, UniqueID oid)
    {
        if (Identifiers.isRootStore(oid)) {
            return "AeroFS";
        }
        // External store
        if (parent == null && Identifiers.isSharedFolder(oid)) {
            return "";
        }
        return PolarisUtilities.stringFromUTF8Bytes(dao.children.getChildName(parent, oid));
    }

    private boolean shouldReturnChildren(DAO dao, UniqueID oid, String[] fields)
    {
        return isFieldPresent(fields, "children") && !objectStore.isFile(dao.objectTypes.get(oid));
    }

    private boolean shouldIncludeInResponse(AeroOAuthPrincipal principal, UniqueID childOID)
    {
        // If the OAuth token has the "linksharing" scope, anchor names should be included iff the
        // owner of the token is a manager of the store.
        Map<Scope, Set<RestObject>> scopes = principal.scope();
        if (!tokenHasPermission(scopes, Scope.LINKSHARE)) return true;
        try {
            objectStore.checkAccess(principal.getUser(), Lists.newArrayList(childOID), MANAGE);
        } catch(AccessException e) {
            return false;
        }
        return true;
    }

    private MetadataAndEtag getObjectMetadata(DAO dao, AeroOAuthPrincipal principal, UniqueID oid,
            @Nullable String queryParam, @Nullable LinkedHashMap<UniqueID, Folder> parentFolders)
    {
        String [] fields = queryParam == null ? null : queryParam.split(",");
        UniqueID parentOID = getParentOID(dao, principal.getUser(), oid);
        String name;
        RestObject parent;
        SID parentSID;
        if (parentOID == null) {
            // If at this stage parent = null, this would mean that it is an external store.
            // Handle it differently from other cases since its a shared folder yet not in the
            // mount points table.
            checkState(Identifiers.isSharedFolder(oid), "Cannot resolve object's parent.");
            // TODO(AS): Name for external root.
            name = "";
            parentSID = SID.anchorOID2storeSID(new OID(oid));
            parent = new RestObject(parentSID, OID.ROOT);
        } else {
            name = resolveName(dao, parentOID, oid);
            // Getting parent SID because for stores we want to return its anchor representation. So we
            // need its parent's store(which would be the root store). For non store objects, the store
            // of its parent and itself will be the same.
            parentSID = new SID(objectStore.getStore(dao, parentOID).getBytes());
            if (Identifiers.isRootStore(parentOID) || Identifiers.isSharedFolder(parentOID)) {
                parent = new RestObject(parentSID, OID.ROOT);
            } else {
                parent = toRestObject(parentSID, parentOID);
            }
        }

        ChildrenList childrenList = shouldReturnChildren(dao, oid, fields)
                ? childrenList(dao, oid, false) : null;
        ParentPath path = parentFolders != null  && isFieldPresent(fields, "path")
                ? parentPath(parentFolders.values()) : null;

        if (l.isDebugEnabled()) {
            if (path != null) {
                l.debug("Path: folder names: {}", path.folders.size() == 0 ? "[]" :
                    Joiner.on(",").join(path.folders.stream().map(o -> o.name).collect(Collectors.toList())));
            } else {
                l.debug("Response path is null");
            }
            if (childrenList != null) {
                l.debug("Children: folder names: {}",
                childrenList.folders.size() == 0 ? "[]" :
                    Joiner.on(",").join(childrenList.folders.stream().map(o -> o.name).collect(Collectors.toList())));
                l.debug("Children file names: {}",
                childrenList.files.size() == 0 ? "[]" :
                    Joiner.on(",").join(childrenList.files.stream().map(o -> o.name).collect(Collectors.toList())));
            } else {
                l.debug("Response children list is null");
            }
        }

        if (objectStore.isFile(dao.objectTypes.get(oid))) {
            Content fp = dao.objectProperties.getLatest(oid);
            Date mtime = getDate(fp);
            Long size = getSize(fp);
            String etag = getContentEtag(fp);
            String mimeType = detector.detect(name);
            return  new MetadataAndEtag(file(toRestObject(parentSID, oid).toStringFormal(), name, parent.toStringFormal(), path,
                    mtime, size, mimeType, etag, null), metaEtag(parent.getOID(), name, fp));
        }
        return new MetadataAndEtag(folder(toRestObject(parentSID, oid).toStringFormal(), name, parent.toStringFormal(),
                path, oid2Sid(oid), childrenList), metaEtag(parent.getOID(), name, null));
    }

    private ChildrenList childrenList(DAO dao, UniqueID oid, boolean shouldIncludeParent)
    {
        List<Folder> folders = Lists.newArrayList();
        List<File> files = Lists.newArrayList();
        SID sid = new SID(objectStore.getStore(dao, oid).getBytes());
        String currentRest = toRestObject(sid, oid).toStringFormal();

        for(Child child: objectStore.children(dao, oid)) {
            OID childOID = new OID(child.oid);
            String name = PolarisUtilities.stringFromUTF8Bytes(child.name);
            String childRest = new RestObject(sid, childOID).toStringFormal();

            if (child.objectType != FILE) {
                String childSid = child.objectType == FOLDER ? null :
                        SID.anchorOID2storeSID(childOID).toStringFormal();
                folders.add(folder(childRest, name, currentRest, null, childSid, null));
            } else {
                Content fp = dao.objectProperties.getLatest(childOID);
                String mimeType = detector.detect(name);
                Date mtime = getDate(fp);
                Long size = getSize(fp);
                String etag = getContentEtag(fp);
                files.add(file(childRest, name, currentRest, null, mtime, size, mimeType, etag,
                        null));
            }
        }
        return new ChildrenList(shouldIncludeParent ? currentRest : null, folders, files);
    }

    private LinkedHashMap<UniqueID, Folder> computeParentFolders(DAO dao, AeroOAuthPrincipal principal,
            UniqueID oid)
    {
        LinkedHashMap<UniqueID, Folder> folders = Maps.newLinkedHashMap();
        if (Identifiers.isRootStore(oid) || dao.children.getParent(oid) == null) {
            return folders;
        }
        UniqueID child = oid;
        UniqueID parent;
        do {
            parent = getParentOID(dao, principal.getUser(), child);
            // External store case. If parent is null and child is a store, reached the root of
            // the external store. Nothing else to compute.
            if (parent == null && Identifiers.isSharedFolder(child)) {
                break;
            }
            if (parent == null || dao.children.isDeleted(parent, child)) {
                throw new NotFoundException(oid);
            }
            folders.put(parent, (Folder) (getObjectMetadata(dao, principal, parent, null, null)).metadata);
            child = parent;
        } while(parent != null && !Identifiers.isRootStore(parent));
        return folders;
    }

    public Response metadata(AeroOAuthPrincipal principal, RestObject object, String queryParam,
            boolean isFile)
    {
        l.info("Get metadata for object {}", object.toStringFormal());
        l.debug("Query params: {}", queryParam);

        UniqueID oid = restObject2OID(principal, object);
        checkAccess(principal, Lists.newArrayList(oid), READ);

        MetadataAndEtag metaAndTag = dbi.inTransaction(READ_COMMITTED,
                (conn, status) -> metadata(new DAO(conn), principal, object, oid, queryParam, isFile));

        CommonMetadata metadata = metaAndTag.metadata;
        if (metadata instanceof Folder && ((Folder) metadata).children != null) {
            List<Folder> folders = ((Folder) metadata).children.folders.stream().filter(folder ->
                    !folder.is_shared || shouldIncludeInResponse(principal,
                            restObject2OID(principal, RestObject.fromString(folder.id))))
                    .collect(Collectors.toList());
            metadata = new Folder(metadata.id, metadata.name, metadata.parent, metadata.path,
                    ((Folder) metadata).sid, new ChildrenList(((Folder) metadata).children.parent, folders,
                    ((Folder) metadata).children.files));
        }
        return Response.ok().entity(metadata).tag(metaAndTag.etag).build();
    }

    private MetadataAndEtag metadata(DAO dao, AeroOAuthPrincipal principal, RestObject object,
            UniqueID oid, String queryParam, boolean isFile)
    {
        if (!isStoreConsistent(principal, dao, oid, object)) {
            throw new NotFoundException(oid);
        }
        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, oid);
        Preconditions.checkState(parentFolders != null, "Parent folders shouldn't be null");
        throwIfInSufficientTokenScope(principal, oid, Scope.READ_FILES, parentFolders);

        if ((objectStore.isFile(dao.objectTypes.get(oid))) != isFile) {
            throw new NotFoundException(oid);
        }

        return getObjectMetadata(dao, principal, oid, queryParam, parentFolders);
    }

    public Response create(AeroOAuthPrincipal principal, String parent, String name,
            Version version, boolean isFile)
    {
        Preconditions.checkArgument(parent != null, "Parent is null.");
        Preconditions.checkArgument(name != null && !name.isEmpty(), "No object name given.");

        RestObject restParent = RestObject.fromString(parent);
        UniqueID parentOID = restObject2OID(principal, restParent);
        AccessToken accessToken = checkAccess(principal, Lists.newArrayList(parentOID),
                READ, WRITE);

        Lock lock = objectStore.lockObject(parentOID);
        try {
            ApiOperationResult result = dbi.inTransaction((conn, status) ->
                    create(new DAO(conn), principal, restParent, parentOID, name, version, accessToken,
                            isFile));
            Map<UniqueID, Long> updatedStores = result.updated.stream()
                    .collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
            updatedStores.forEach(notifier::notifyStoreUpdated);
            return result.response;
        } finally {
            lock.unlock();
        }
    }

    private ApiOperationResult create(DAO dao, AeroOAuthPrincipal principal, RestObject object,
            UniqueID parentOID, String name, Version version, AccessToken accessToken,
            boolean isFile)
    {
        if (!isStoreConsistent(principal, dao, parentOID, object)) {
            throw new NotFoundException(parentOID);
        }
        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, parentOID);
        throwIfInSufficientTokenScope(principal, parentOID, Scope.WRITE_FILES, parentFolders);
        if (objectStore.isFile(dao.objectTypes.get(parentOID))) {
            return new ApiOperationResult(Lists.newArrayList(), Response.status(BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(new Error(Error.Type.BAD_ARGS, "Cannot create an object under a file"))
                    .build());
        }

        OID oid = OID.generate();
        l.info("Create object. parent {} object name {} oid {}", parentOID, name, oid);
        OperationResult result = objectStore.performTransform(dao, accessToken, principal.getDID(),
                parentOID, new InsertChild(oid, isFile ? FILE : FOLDER, name, null));
        l.info("Result for creating object {}: {}", name, result);

        String location = EXTERNAL_API_LOCATION
                + "v" + version
                +  (isFile ? "/files" : "/folders")
                + "/" + toRestObject(new SID(objectStore.getStore(dao, oid)), oid).toStringFormal();

        MetadataAndEtag metadataAndEtag = getObjectMetadata(dao, principal, oid, null, null);
        return new ApiOperationResult(result.updated, Response.created(URI.create(location))
                .entity(metadataAndEtag.metadata)
                .tag(metadataAndEtag.etag)
                .build());
   }

    public Response move(AeroOAuthPrincipal principal, RestObject object, String parent, String name, EntityTagSet ifmatch) {
        Preconditions.checkArgument(parent != null, "Destination is null.");
        Preconditions.checkArgument(name != null && !name.isEmpty(), "No object name given.");

        UniqueID oid = restObject2OID(principal, object);
        UniqueID toParent = restObject2OID(principal, RestObject.fromString(parent));

        // Keep track of parent OID and store OIDs required for use later in the same operation but
        // outside the transaction. We need to do this because we need to:
        // 1. Check Access for stores outside transactions because that needs to network with
        // Sparta.
        // 2. Be able to lock objects outside transaction.
        ParentAndStores ps = dbi.inTransaction((conn, Status) -> {
            DAO dao = new DAO(conn);
            UniqueID parentOID = getParentOID(dao, principal.getUser(), oid);
            if (parentOID == null) {
                throw new NotFoundException(oid);
            }
            return new ParentAndStores(parentOID,
                    Sets.newHashSet(objectStore.getStore(dao, parentOID), objectStore.getStore(dao, toParent)));
        });

        AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(), ps.stores,
                READ, WRITE);

        Lock lock = objectStore.lockObject(ps.parent);
        try {
            ApiOperationResult result = dbi.inTransaction((conn, status) ->
                    move(new DAO(conn), principal, object, RestObject.fromString(parent), oid,
                            toParent, name, accessToken, ifmatch));
            Map<UniqueID, Long> updatedStores = result.updated.stream()
                    .collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
            updatedStores.forEach(notifier::notifyStoreUpdated);
            try {
                if (new OID(oid).isAnchor()) {
                    sn.setPersonalStoreName(new AeroDelegatedUserDevicePrincipal(
                            "polaris", principal.getUser(), principal.getDID()), oid, name);
                }
            } catch (Exception e) {
                l.info("failed to update shared folder name", e);
            }
            return result.response;
        } finally {
            lock.unlock();
        }
    }

    private ApiOperationResult move(DAO dao, AeroOAuthPrincipal principal, RestObject object,
            RestObject toParentObject, UniqueID oid, UniqueID toParent, String name, AccessToken accessToken, EntityTagSet ifmatch)
    {
        if (!isStoreConsistent(principal, dao, oid, object) ||
                !isStoreConsistent(principal, dao, toParent, toParentObject)) {
            throw new NotFoundException(oid);
        }

        UniqueID fromParent = getParentOID(dao, principal.getUser(), oid);
        if (fromParent == null) {
            throw new NotFoundException(oid);
        }

        if (ifmatch.isValid()) {
            EntityTag currentTag = metaEtag(restObjectOID(fromParent), PolarisUtilities.stringFromUTF8Bytes(dao.children.getChildName(fromParent, oid)), dao.objectProperties.getLatest(oid));
            if (!ifmatch.matches(currentTag)) {
                throw new ConditionFailedException();
            }
        }

        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, oid);
        throwIfInSufficientTokenScope(principal, oid, Scope.WRITE_FILES, parentFolders);
        parentFolders = computeParentFolders(dao, principal, toParent);
        throwIfInSufficientTokenScope(principal, toParent, Scope.WRITE_FILES, parentFolders);

        l.info("Move {} from {} to {} as {}", oid, fromParent, toParent, name);
        OperationResult result = objectStore.performTransform(dao, accessToken, principal.getDID(),
                fromParent, new MoveChild(oid, toParent, name));
        UniqueID child = dao.children.getActiveChildNamed(toParent, PolarisUtilities.stringToUTF8Bytes(name));
        checkState(child != null, "cannot find child that was just moved");
        MetadataAndEtag metadataAndEtag = getObjectMetadata(dao, principal, child, null, null);
        return new ApiOperationResult(result.updated, Response.ok()
                .entity(metadataAndEtag.metadata)
                .tag(metadataAndEtag.etag)
                .build());
    }

    public Response delete(AeroOAuthPrincipal principal, RestObject object, EntityTagSet ifmatch)
    {
        l.info("Deleting object {}", object.toStringFormal());

        UniqueID oid = restObject2OID(principal, object);
        ParentAndStores ps = dbi.inTransaction((conn, Status) -> {
            DAO dao = new DAO(conn);
            UniqueID parent = getParentOID(dao, principal.getUser(), oid);
            if (parent == null) {
                throw new NotFoundException(oid);
            }
            return new ParentAndStores(parent, Sets.newHashSet(objectStore.getStore(dao, parent)));
        });

        Lock lock = objectStore.lockObject(ps.parent);
        try {
            AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(),
                    ps.stores, READ, WRITE);
            ApiOperationResult result = dbi.inTransaction((conn, status) ->
                    delete(new DAO(conn), principal, object, oid, accessToken, ifmatch));
            Map<UniqueID, Long> updatedStores = result.updated.stream()
                    .collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
            updatedStores.forEach(notifier::notifyStoreUpdated);
            return result.response;
        } finally {
            lock.unlock();
        }
    }

    private ApiOperationResult delete(DAO dao, AeroOAuthPrincipal principal, RestObject object,
            UniqueID oid, AccessToken accessToken, EntityTagSet ifmatch)
    {
        if (!isStoreConsistent(principal, dao, oid, object)) {
            throw new NotFoundException(oid);
        }

        UniqueID parent = getParentOID(dao, principal.getUser(), oid);
        if (ifmatch.isValid()) {
            EntityTag currentTag = metaEtag(restObjectOID(parent), PolarisUtilities.stringFromUTF8Bytes(dao.children.getChildName(parent, oid)), dao.objectProperties.getLatest(oid));
            if (!ifmatch.matches(currentTag)) {
                throw new ConditionFailedException();
            }
        }

        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, oid);
        throwIfInSufficientTokenScope(principal, oid, Scope.WRITE_FILES, parentFolders);
        Preconditions.checkArgument(!Identifiers.isRootStore(oid), "cannot remove user root");

        OperationResult result = objectStore.performTransform(dao, accessToken,
                principal.getDID(), parent, new RemoveChild(new OID(oid)));

        l.info("Result for deleting object {}: {}", oid.toStringFormal(), result);
        return new ApiOperationResult(result.updated, Response.noContent().build());
    }

    public Response children(AeroOAuthPrincipal principal, RestObject object, boolean includeParent)
    {
        l.info("Get children for: {}", object.toStringFormal());
        UniqueID oid = restObject2OID(principal, object);
        checkAccess(principal, Lists.newArrayList(oid), READ);
        ChildrenList childrenList = dbi.inTransaction(READ_COMMITTED,
                (conn, status) -> children(new DAO(conn), principal, object, oid, includeParent));

        // N.B.Filter out child folders that shouldn't be displayed if accessing link.
        // This is avoid network calls in trans.
        List<Folder> folders = childrenList.folders.stream().filter(folder ->
                !folder.is_shared || shouldIncludeInResponse(principal,
                        restObject2OID(principal, RestObject.fromString(folder.id))))
                .collect(Collectors.toList());
        ChildrenList filtered = new ChildrenList(childrenList.parent, folders, childrenList.files);
        return Response.ok()
                .entity(filtered)
                .build();
    }

    private ChildrenList children(DAO dao, AeroOAuthPrincipal principal, RestObject object,
            UniqueID oid, boolean includeParent)
    {
        if (!isStoreConsistent(principal, dao, oid, object)) {
            throw new NotFoundException(oid);
        }
        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, oid);
        throwIfInSufficientTokenScope(principal, oid, Scope.READ_FILES, parentFolders);
        return childrenList(dao, oid, includeParent);
    }

    public Response path(AeroOAuthPrincipal principal, RestObject object)
    {
        l.info("Get path for: {}", object.toStringFormal());
        UniqueID oid = restObject2OID(principal, object);

        checkAccess(principal, Lists.newArrayList(oid), READ);
        return dbi.inTransaction(READ_COMMITTED,
                (conn, status) -> path(new DAO(conn), principal, object, oid));
    }

    private ParentPath parentPath(Collection<Folder> parentFolders)
    {
        List<Folder> folders = Lists.newArrayList(parentFolders);
        // The parentFolders are accumulated in a bottoms up manner, however the Response sent to
        // the clients must but in a top to bottom manner. Hence, reverse.
        Collections.reverse(folders);
        return new ParentPath(folders);
    }

    private Response path(DAO dao, AeroOAuthPrincipal principal, RestObject object, UniqueID oid)
    {
        if (!isStoreConsistent(principal, dao, oid, object)) {
            throw new NotFoundException(oid);
        }
        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, oid);
        throwIfInSufficientTokenScope(principal, oid, Scope.READ_FILES, parentFolders);
        return Response.ok().entity(parentPath(parentFolders.values())).build();
    }

    // Check if folder has already been shared by checking if the oid passed in is a shared folder
    // id itself or the folder id of an already existing shared folder.
    private boolean isAlreadyShared(DAO dao, UniqueID oid)
    {
        return Identifiers.isSharedFolder(oid) ||
                objectStore.doesExist(dao, SID.folderOID2convertedAnchorOID(new OID(oid)));
    }

    /**
     * Share an existing folder. If the object passed in already shared, then just make the
     * corresponding call to sparta without making any polaris db changes. This is done because
     * it is possible that a previous shared operation on the same object might have made the
     * necessary polaris db changes (which is how we would know the object passed in is already
     * shared) but the sparta call might have failed. So regardless of the kind of
     * object being passed in, make the necessary sparta call.
     */
    public Response share(AeroOAuthPrincipal principal, RestObject  object)
    {
        UniqueID oid = restObject2OID(principal, object);

        ParentAndStores ps = dbi.inTransaction((conn, Status) -> {
            DAO dao = new DAO(conn);
            UniqueID parentOID;
            // If SID of the passed in object is not root SID throw. However, only do this when
            // passed in object is not a shared folder because we still want to make a sparta call
            // in the case that earlier sparta call might have failed.
            if (isAlreadyShared(dao, oid)) {
                UniqueID sid = Identifiers.isSharedFolder(oid) ? oid :
                        SID.folderOID2convertedAnchorOID(new OID(oid));
                parentOID = getParentOID(dao, principal.getUser(), sid);
            } else {
                Preconditions.checkArgument(SID.rootSID(principal.getUser()).equals(object.getSID()),
                        "Cannot share a child of a shared folder");
                parentOID = getParentOID(dao, principal.getUser(), oid);
            }
            if (parentOID == null) {
                throw new NotFoundException(oid);
            }
            return new ParentAndStores(parentOID,
                    Sets.newHashSet(objectStore.getStore(dao, parentOID)));
        });

        SettableFuture<String> future = SettableFuture.create();
        AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(),
                ps.stores, READ, WRITE);

        Lock lock = objectStore.lockObject(ps.parent);
        try {
            ApiOperationResult result = dbi.inTransaction((conn, status) ->
                    share(new DAO(conn), principal, accessToken, ps.parent, object, oid, future));
            checkState(result.updated.size() == 0, "Create share folder. No updated stores");

            OID resultOID = RestObject.fromString(((CommonMetadata)result.response.getEntity()).id).getOID();
            if (!fs.shareFolder(principal, new SID(resultOID.getBytes()), future.get())) {
                return Response.status(INTERNAL_SERVER_ERROR).build();
            }
            return result.response;
        } catch (InterruptedException|ExecutionException e) {
            l.error("Unable to compute name of folder to be shared: {} ", e);
            return Response.status(INTERNAL_SERVER_ERROR).build();
        } finally {
            lock.unlock();
        }
    }

    private ApiOperationResult share(DAO dao, AeroOAuthPrincipal principal, AccessToken accessToken,
            UniqueID parentOID, RestObject object, UniqueID oid, SettableFuture<String> future)
    {
        if (!isStoreConsistent(principal, dao, oid, object)) {
            throw new NotFoundException(oid);
        }
        List<Updated> updated = Lists.newArrayList();
        OID anchorOID =  SID.folderOID2convertedAnchorOID(new OID(oid));

        if (isAlreadyShared(dao, oid)) {
            future.set(PolarisUtilities.stringFromUTF8Bytes(dao.children.getActiveChildName(parentOID, anchorOID)));
        } else {
            future.set(PolarisUtilities.stringFromUTF8Bytes(dao.children.getActiveChildName(parentOID, oid)));
            l.info("Share folder: {}", object.toStringFormal());
            updated = objectStore.performTransform(dao, accessToken, principal.getDID(),
                    parentOID, new Share(oid)).updated;
            l.info("Shared object {} updated {}", oid.toStringFormal(), updated);
        }
        MetadataAndEtag metadataAndEtag = getObjectMetadata(dao, principal, anchorOID, null, null);
        return new ApiOperationResult(updated, Response.ok()
                .entity(metadataAndEtag.metadata)
                .tag(metadataAndEtag.etag)
                .build());
    }

    private static EntityTag metaEtag(UniqueID parent, String name, @Nullable Content content)
    {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        md.update(parent.getBytes());
        md.update(BaseUtil.string2utf(name));
        if (content != null) {
            md.update(BaseUtil.toByteArray(content.size));
            md.update(BaseUtil.toByteArray(content.mtime));
        }
        return new EntityTag(BaseUtil.hexEncode(md.digest()), true);
    }

    // POJO to store parent OID and store OID for later use within an API operation since both are
    // computed within a single transaction.
    private static final class ParentAndStores
    {
        @NotNull
        @Valid
        public final UniqueID parent;

        @NotNull
        public final Set<UniqueID> stores;

        public ParentAndStores(UniqueID parent, Set<UniqueID> stores)
        {
            this.parent = parent;
            this.stores = stores;
        }
    }

    private static final class ApiOperationResult
    {
        @NotNull
        @Valid
        public final List<Updated> updated;

        @NotNull
        public final Response response;

        public ApiOperationResult(List<Updated> updated, Response response)
        {
            this.updated = updated;
            this.response = response;
        }
    }

    private static final class MetadataAndEtag
    {
        public final CommonMetadata metadata;
        public final EntityTag etag;

        public MetadataAndEtag(CommonMetadata metadata, EntityTag etag)
        {
            this.metadata = metadata;
            this.etag = etag;
        }
    }

    private static OID restObjectOID(UniqueID id)
    {
        return Identifiers.isSharedFolder(id) || Identifiers.isRootStore(id) ? OID.ROOT : new OID(id);
    }
}
