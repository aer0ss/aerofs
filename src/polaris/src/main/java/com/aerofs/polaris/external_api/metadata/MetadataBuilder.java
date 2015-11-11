package com.aerofs.polaris.external_api.metadata;

import com.aerofs.auth.server.AeroOAuthPrincipal;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.RestObject;
import com.aerofs.ids.*;
import com.aerofs.oauth.Scope;
import com.aerofs.polaris.acl.Access;
import com.aerofs.polaris.acl.AccessException;
import com.aerofs.polaris.api.operation.*;
import com.aerofs.polaris.api.types.Child;
import com.aerofs.polaris.api.types.Content;
import com.aerofs.polaris.external_api.rest.util.Version;
import com.aerofs.polaris.logical.DAO;
import com.aerofs.polaris.logical.NotFoundException;
import com.aerofs.polaris.logical.ObjectStore;
import com.aerofs.polaris.notification.Notifier;
import com.aerofs.rest.api.*;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.util.MimeTypeDetector;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import org.skife.jdbi.v2.DBI;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static com.aerofs.polaris.acl.Access.*;
import static com.aerofs.polaris.api.types.ObjectType.FILE;
import static com.aerofs.polaris.api.types.ObjectType.FOLDER;
import static com.aerofs.polaris.external_api.Constants.APPDATA_FOLDER_NAME;
import static com.aerofs.polaris.external_api.Constants.EXTERNAL_API_LOCATION;
import static com.aerofs.polaris.external_api.metadata.RestObjectResolver.toRestObject;
import static com.aerofs.polaris.external_api.metadata.RestObjectResolver.fromRestObject;
import static com.aerofs.polaris.logical.ObjectStore.AccessToken;
import static com.aerofs.rest.api.File.ContentState;
import static org.skife.jdbi.v2.TransactionIsolationLevel.*;

@Singleton
public class MetadataBuilder
{
    private static final Logger l = Loggers.getLogger(MetadataBuilder.class);

    private final MimeTypeDetector detector;
    private final ObjectStore objectStore;
    private final Notifier notifier;
    private final DBI dbi;

    @Inject
    public MetadataBuilder(@Context ObjectStore objectStore, @Context MimeTypeDetector detector,
            @Context Notifier notifier, @Context DBI dbi)
    {
        this.objectStore = objectStore;
        this.detector = detector;
        this.notifier = notifier;
        this.dbi = dbi;
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
        if (hasUnrestrictedPermission(qualifiedScopes, scope)) return true ;

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
                    .status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(new Error(Error.Type.FORBIDDEN, "Token lacks required scope"))
                    .build());
        }
    }

    private UniqueID createAppDataIfNecessary(DAO dao, AeroOAuthPrincipal principal,
            List<Updated> updated)
    {
        SID rootSID = SID.rootSID(principal.getUser());
        UniqueID appDataId = dao.children.getActiveChildNamed(rootSID, APPDATA_FOLDER_NAME.getBytes());
        if (appDataId == null) {
            // Create .appdata
            appDataId = OID.generate();
            // N.B. Any checkAccess call made in this function will not require a network call to
            // Sparta because the store for both the appdata folder and the {client_id} folder
            // is the root folder of the user, for which we don't have to check with Sparta. We
            // still call this method to get the access token for performTransform.
            AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(),
                    Sets.newHashSet(objectStore.getStore(dao, rootSID)), READ, WRITE);
            updated.addAll(performTransform(dao, accessToken,
                    principal.getDID(), rootSID,
                    new InsertChild(appDataId, FOLDER, APPDATA_FOLDER_NAME, null)).updated);
        }

        UniqueID clientId = dao.children.getActiveChildNamed(appDataId, principal.audience().getBytes());
        if (clientId == null) {
            // Create .appdata/{clientId}
            // N.B.: Hack to get oid of the created child back to the callee of this method. We want to
            // avoid notifying updated stores in a DB trans, which necessitates need to send
            // back/accumulate list of updated stores. However, we also want the oid of the
            // .appdata/{client id} folder if it exists or if we had to created it.
            clientId = OID.generate();
            AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(),
                    Sets.newHashSet(objectStore.getStore(dao, appDataId)), READ, WRITE);
            updated.addAll(performTransform(dao, accessToken,
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
            oid = dbi.inTransaction((conn, status) ->
                    createAppDataIfNecessary(new DAO(conn), principal, updated));
            Map<UniqueID, Long> updatedStores = updated.stream()
                    .collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
            updatedStores.forEach(notifier::notifyStoreUpdated);
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
        String storeFromRestObject;
        if (object.isRoot() || object.isAppData()) {
            storeFromRestObject = SID.rootSID(principal.getUser()).toStringFormal();
        } else if (object.getOID().isRoot() || !Identifiers.isSharedFolder(object.getOID())) {
            storeFromRestObject = object.getSID().toStringFormal();
        } else {
            storeFromRestObject = object.getOID().toStringFormal();
        }
        return store != null && store.toStringFormal().equals(storeFromRestObject);
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
        return new String(dao.children.getChildName(parent, oid));
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

    private CommonMetadata getObjectMetadata(DAO dao, AeroOAuthPrincipal principal, UniqueID oid,
            @Nullable String queryParam, @Nullable LinkedHashMap<UniqueID, Folder> parentFolders)
    {
        String [] fields = queryParam == null ? null : queryParam.split(",");
        UniqueID parentOID = getParentOID(dao, principal.getUser(), oid);
        String name, parent;
        SID parentSID;
        if (parentOID == null) {
            // If at this stage parent = null, this would mean that it is an external store.
            // Handle it differently from other cases since its a shared folder yet not in the
            // mount points table.
            Preconditions.checkState(Identifiers.isSharedFolder(oid), "Cannot resolve object's parent.");
            // TODO(AS): Name for external root.
            name = "";
            parentSID = new SID(oid.getBytes());
            parent = new RestObject(parentSID, OID.ROOT).toStringFormal();
        } else {
            name = resolveName(dao, parentOID, oid);
            // Getting parent SID because for stores we want to return its anchor representation. So we
            // need its parent's store(which would be the root store). For non store objects, the store
            // of its parent and itself will be the same.
            parentSID = new SID(objectStore.getStore(dao, parentOID).getBytes());
            if (Identifiers.isRootStore(parentOID) || Identifiers.isSharedFolder(parentOID)) {
                parent = new RestObject(parentSID, OID.ROOT).toStringFormal();
            } else {
                parent = toRestObject(parentSID, parentOID);
            }
        }

        ChildrenList childrenList = shouldReturnChildren(dao, oid, fields)
                ? childrenList(dao, oid, false) : null;
        ParentPath path = isFieldPresent(fields, "path") && parentFolders != null
                ? parentPath(parentFolders.values()) : null;

        if (objectStore.isFile(dao.objectTypes.get(oid))) {
            Content fp = dao.objectProperties.getLatest(oid);
            Date mtime = getDate(fp);
            Long size = getSize(fp);
            String etag = getContentEtag(fp);
            String mimeType = detector.detect(name);
            return  file(toRestObject(parentSID, oid), name, parent, path,
                    mtime, size, mimeType, etag, null);
        }
        return folder(toRestObject(parentSID, oid), name, parent,
                path, oid2Sid(oid), childrenList);
    }

    private ChildrenList childrenList(DAO dao, UniqueID oid, boolean shouldIncludeParent)
    {
        List<Folder> folders = Lists.newArrayList();
        List<File> files = Lists.newArrayList();
        SID sid = new SID(objectStore.getStore(dao, oid).getBytes());
        String currentRest = toRestObject(sid, oid);

        for(Child child: objectStore.children(dao, oid)) {
            OID childOID = new OID(child.oid);
            String name = new String(child.name);
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
            folders.put(parent, (Folder) getObjectMetadata(dao, principal, parent, null, null));
            child = parent;
        } while(parent != null && !Identifiers.isRootStore(parent));
        return folders;
    }

    public Response metadata(AeroOAuthPrincipal principal, RestObject object, String queryParam,
            boolean isFile)
    {
        l.info("Get metadata for object {}", object.toStringFormal());
        UniqueID oid = restObject2OID(principal, object);
        checkAccess(principal, Lists.newArrayList(oid), READ);

        CommonMetadata metadata = dbi.inTransaction(READ_COMMITTED,
                (conn, status) -> metadata(new DAO(conn), principal, object, oid, queryParam, isFile));

        if (metadata instanceof Folder && ((Folder) metadata).children != null) {
            List<Folder> folders = ((Folder) metadata).children.folders.stream().filter(folder ->
                    !folder.is_shared || shouldIncludeInResponse(principal,
                            restObject2OID(principal, RestObject.fromString(folder.id))))
                    .collect(Collectors.toList());
            metadata = new Folder(metadata.id, metadata.name, metadata.parent, metadata.path,
                    ((Folder) metadata).sid, new ChildrenList(((Folder) metadata).children.parent, folders,
                    ((Folder) metadata).children.files));
        }
        return Response.ok().entity(metadata).build();
    }

    private CommonMetadata metadata(DAO dao, AeroOAuthPrincipal principal, RestObject object,
            UniqueID oid, String queryParam, boolean isFile)
    {
        if (!isStoreConsistent(principal, dao, oid, object)) {
            throw new NotFoundException(oid);
        }
        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, oid);
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
        Preconditions.checkArgument(name != null, "No object name given.");

        RestObject restParent = RestObject.fromString(parent);
        UniqueID parentOID = restObject2OID(principal, restParent);
        AccessToken accessToken = checkAccess(principal, Lists.newArrayList(parentOID),
                READ, WRITE);

        ApiOperationResult result  = dbi.inTransaction((conn, status) ->
                create(new DAO(conn), principal, restParent, parentOID, name, version, accessToken,
                        isFile));
        Map<UniqueID, Long> updatedStores =result.updated.stream()
                .collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
        updatedStores.forEach(notifier::notifyStoreUpdated);
        return result.response;
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
            return new ApiOperationResult(Lists.newArrayList(), Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(new Error(Error.Type.BAD_ARGS, "Cannot create an object under a file"))
                    .build());
        }

        OID oid = OID.generate();
        l.info("Create object. parent {} object name {} oid {}", parentOID, name, oid);
        OperationResult result = performTransform(dao, accessToken, principal.getDID(),
                parentOID, new InsertChild(oid, isFile ? FILE : FOLDER, name, null));
        l.info("Result for creating object {}: {}", name, result);

        String location = EXTERNAL_API_LOCATION
                + "v" + version
                +  (isFile ? "/files" : "/folders")
                + "/" + toRestObject(new SID(objectStore.getStore(dao, oid)), oid);

        return new ApiOperationResult(result.updated, Response.created(URI.create(location))
                .entity(getObjectMetadata(dao, principal, oid, null, null))
                .build());
   }

    public Response move(AeroOAuthPrincipal principal, RestObject object, String parent, String name)
    {
        Preconditions.checkArgument(parent != null, "Destination is null.");
        Preconditions.checkArgument(name != null, "No object name given.");

        UniqueID oid = restObject2OID(principal, object);
        UniqueID toParent = restObject2OID(principal, RestObject.fromString(parent));
        Set<UniqueID> stores = dbi.inTransaction((conn, Status) -> {
            DAO dao = new DAO(conn);
            UniqueID parentOID = getParentOID(dao, principal.getUser(), oid);
            if (parentOID == null) {
                throw new NotFoundException(oid);
            }
            return Sets.newHashSet(objectStore.getStore(dao, parentOID), objectStore.getStore(dao, toParent));
        });

        AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(), stores,
                READ, WRITE);
        ApiOperationResult result  = dbi.inTransaction((conn, status) ->
                move(new DAO(conn), principal, object, RestObject.fromString(parent), oid,
                        toParent, name, accessToken));
        Map<UniqueID, Long> updatedStores = result.updated.stream()
                .collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
        updatedStores.forEach(notifier::notifyStoreUpdated);
        return result.response;
    }

    private ApiOperationResult move(DAO dao, AeroOAuthPrincipal principal, RestObject object,
            RestObject toParentObject, UniqueID oid, UniqueID toParent, String name, AccessToken accessToken)
    {
        if (!isStoreConsistent(principal, dao, oid, object) ||
                !isStoreConsistent(principal, dao, toParent, toParentObject)) {
            throw new NotFoundException(oid);
        }
        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, oid);
        throwIfInSufficientTokenScope(principal, oid, Scope.WRITE_FILES, parentFolders);
        parentFolders = computeParentFolders(dao, principal, toParent);
        throwIfInSufficientTokenScope(principal, toParent, Scope.WRITE_FILES, parentFolders);

        UniqueID fromParent = getParentOID(dao, principal.getUser(), oid);
        if (fromParent == null) {
            throw new NotFoundException(oid);
        }

        l.info("Move object <oid, name> <{}, {}> from {} to {}", oid, name, fromParent, toParent);
        OperationResult result = performTransform(dao, accessToken, principal.getDID(),
                fromParent, new MoveChild(oid, toParent, name));
        UniqueID child = dao.children.getActiveChildNamed(toParent, name.getBytes());
        return new ApiOperationResult(result.updated, Response.ok()
                .entity(getObjectMetadata(dao, principal, child, null, null))
                .build());
    }

    public Response delete(AeroOAuthPrincipal principal, RestObject object)
    {
        l.info("Deleting object {}", object.toStringFormal());

        UniqueID oid = restObject2OID(principal, object);
        Set<UniqueID> parentStores = dbi.inTransaction((conn, Status) -> {
            DAO dao = new DAO(conn);
            UniqueID parent = getParentOID(dao, principal.getUser(), oid);
            if (parent == null) {
                throw new NotFoundException(oid);
            }
            return Sets.newHashSet(objectStore.getStore(dao, parent));
        });

        AccessToken accessToken = objectStore.checkAccessForStores(principal.getUser(),
                parentStores, READ, WRITE);
        ApiOperationResult result = dbi.inTransaction((conn, status) ->
                delete(new DAO(conn), principal, object, oid, accessToken));
        Map<UniqueID, Long> updatedStores =result.updated.stream()
                .collect(Collectors.toMap(x -> x.object.store, x -> x.transformTimestamp, Math::max));
        updatedStores.forEach(notifier::notifyStoreUpdated);
        return result.response;
    }

    private ApiOperationResult delete(DAO dao, AeroOAuthPrincipal principal, RestObject object,
            UniqueID oid, AccessToken accessToken)
    {
        if (!isStoreConsistent(principal, dao, oid, object)) {
            throw new NotFoundException(oid);
        }
        LinkedHashMap<UniqueID, Folder> parentFolders = computeParentFolders(dao, principal, oid);
        throwIfInSufficientTokenScope(principal, oid, Scope.WRITE_FILES, parentFolders);
        Preconditions.checkArgument(!Identifiers.isRootStore(oid), "cannot remove user root");

        UniqueID parent = getParentOID(dao, principal.getUser(), oid);
        OperationResult result = performTransform(dao, accessToken,
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

    private OperationResult performTransform(DAO dao, AccessToken token, DID device, UniqueID oid, Operation operation)
    {
        Lock l = objectStore.lockObject(oid);
        try {
            return objectStore.performTransform(dao, token, device, oid, operation);
        } finally {
            l.unlock();
        }
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

    public static final class ApiOperationResult
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
}
