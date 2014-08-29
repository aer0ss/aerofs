/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.util;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.acl.Permissions.Permission;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.CfgAbsRoots;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.ChildrenList;
import com.aerofs.rest.api.CommonMetadata;
import com.aerofs.rest.api.File;
import com.aerofs.rest.api.File.ContentState;
import com.aerofs.rest.api.Folder;
import com.aerofs.rest.api.ParentPath;
import com.aerofs.rest.util.OAuthToken;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class MetadataBuilder
{
    private final IStores _stores;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;
    private final MimeTypeDetector _detector;
    private final EntityTagUtil _etags;
    private final LocalACL _acl;
    private final ICollectorStateDatabase _csdb;
    private final CfgAbsRoots _cfgAbsRoots;

    @Inject
    public MetadataBuilder(DirectoryService ds, IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx,
            IStores stores, MimeTypeDetector detector, EntityTagUtil etags, LocalACL acl,
            ICollectorStateDatabase csdb, CfgAbsRoots cfgAbsRoots)
    {
        _ds = ds;
        _stores = stores;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
        _detector = detector;
        _etags = etags;
        _acl = acl;
        _csdb = csdb;
        _cfgAbsRoots = cfgAbsRoots;
    }

    public RestObject object(SOID soid) throws ExNotFound
    {
        return new RestObject(_sidx2sid.getThrows_(soid.sidx()), soid.oid());
    }

    public CommonMetadata metadata(SOID soid, OAuthToken token) throws ExNotFound, SQLException
    {
        return metadata(_ds.getOAThrows_(soid), token, null);
    }

    /**
     * The API should only ever return a store's root dir if the store is external for the
     * user making the request. In all other case, it should return the appropriate anchor
     * for the user making the request.
     *
     * This is necessary to get consistent parent/child values and to provide a consistent
     * view to the user when the request is served by a Team Server.
     */
    private SOID selfOrAnchorForUser(SOID soid, UserID user) throws SQLException
    {
        if (!soid.oid().isRoot()) return soid;
        SID root = SID.rootSID(user);
        SID sid = _sidx2sid.get_(soid.sidx());
        SIndex sidxRoot = _sid2sidx.get_(root);
        return _stores.getParents_(soid.sidx()).contains(sidxRoot)
                ? new SOID(sidxRoot, SID.storeSID2anchorOID(sid)) : soid;
    }

    private RestObject parent(OA oa, OAuthToken token) throws SQLException
    {
        SIndex sidx = oa.soid().sidx();
        if (oa.parent().isRoot()) {
            SOID soidParent = selfOrAnchorForUser(new SOID(sidx, oa.parent()), token.user());
            return new RestObject(_sidx2sid.get_(soidParent.sidx()), soidParent.oid());
        }
        return new RestObject(_sidx2sid.get_(sidx), oa.parent());
    }

    public CommonMetadata metadata(OA oa, OAuthToken token, Fields fields) throws ExNotFound, SQLException
    {
        SOID soid = selfOrAnchorForUser(oa.soid(), token.user());
        if (!soid.equals(oa.soid())) oa = _ds.getOA_(soid);

        SID sid = _sidx2sid.get_(soid.sidx());
        String object = new RestObject(sid, soid.oid()).toStringFormal();
        String parent = parent(oa, token).toStringFormal();

        ParentPath path = fields != null && fields.isRequested("path")
                ? path(_ds.resolve_(oa), token) : null;
        ChildrenList children = null;
        if (oa.isDirOrAnchor() && fields != null && fields.isRequested("children")) {
            OA oaDir = oa;
            if (oa.isAnchor()) {
                SOID soidDir = _ds.followAnchorNullable_(oa);
                oaDir = soidDir != null ? _ds.getOANullable_(soidDir) : null;
            }
            if (oaDir != null) {
                children = children(object, oaDir, false, token);
            } else {
                children = new ChildrenList(null, Collections.<Folder>emptyList(),
                        Collections.<File>emptyList());
            }
        }

        return oa.isDirOrAnchor()
                ? folder(oa, object, parent, path, children)
                : file(oa, object, parent, path);
    }

    /**
     * For a given root - which may be a shared folder or the user's root store,
     * get a reasonable human-readable name.
     *
     * If the object given is the root store, the name is assumed to be AeroFS.
     *
     * If the object given is the root of an external folder, we look it up from the db.
     *
     * NOTE this returns the folder name of the external folder on _this_ client only. There
     * is no "global" name for an external folder, really.
     */
    private String getRootFolderName(OA oa) throws SQLException
    {
        ResolvedPath resolvedPath = _ds.resolve_(oa);
        String absPath = _cfgAbsRoots.getNullable(resolvedPath.sid());

        return (resolvedPath.sid().isUserRoot()) ?
                "AeroFS" : new java.io.File(absPath).getName();
    }

    private Folder folder(OA oa, String object, String parent, @Nullable ParentPath path,
            ChildrenList children) throws SQLException
    {
        OID oid = oa.soid().oid();
        String name = oid.isRoot() ? getRootFolderName(oa) : oa.name();
        String sid = oa.isAnchor() ? SID.anchorOID2storeSID(oid).toStringFormal() : null;
        return new Folder(object, name, parent, path, sid, children);
    }

    private File file(OA oa, String object, String parent, @Nullable ParentPath path)
            throws SQLException
    {
        String mimeType = _detector.detect(oa.name());
        String etag =  _etags.etagForContent(oa.soid()).getValue();

        ContentState state;

        if (oa.isExpelled()) {
            state = ContentState.DESELECTED;
        } else {
            CA ca = oa.caMasterNullable();
            if (ca != null) {
                return new File(object, oa.name(), parent, path, new Date(ca.mtime()), ca.length(),
                        mimeType, etag, ContentState.AVAILABLE);
            }
            state = _csdb.isCollectingContent_(oa.soid().sidx())
                    ? ContentState.SYNCING : ContentState.INSUFFICIENT_STORAGE;
        }
        return new File(object, oa.name(), parent, state, mimeType, etag, path);
    }

    public ParentPath path(ResolvedPath path, OAuthToken token) throws ExNotFound, SQLException
    {
        // On TS, DirectoryService resolve path up to the store root but to provide consistent
        // API responses we need to take the userid into account and resolve the path within
        // their root folder if possible
        if (L.isMultiuser() && !path.sid().isUserRoot())  {
            SID root = SID.rootSID(token.user());
            SIndex sidxRoot = _sid2sidx.get_(root);
            SIndex sidxChild = _sid2sidx.get_(path.sid());
            if (_stores.getParents_(sidxChild).contains(sidxRoot)) {
                SOID anchor = new SOID(sidxRoot, SID.storeSID2anchorOID(path.sid()));
                ResolvedPath anchorPath = _ds.resolve_(anchor);
                path = anchorPath.join(path);
            }
        }
        // TODO: determine name for alternate roots (aka external shares)
        List<Folder> folders = Lists.newArrayList();
        if (!path.isEmpty()) {
            folders.add(new Folder(new RestObject(path.sid(), OID.ROOT).toStringFormal(),
                    path.sid().isUserRoot() ? "AeroFS" :  "",
                    path.sid().isUserRoot() ? null : path.sid().toStringFormal()));
        }

        for (int i = 0; i < path.soids.size() - 1; ++i) {
            folders.add((Folder)metadata(path.soids.get(i), token));
        }

        return new ParentPath(folders);
    }

    public ChildrenList children(String parent, OA oa, boolean includeParent, OAuthToken token)
            throws ExNotFound, SQLException
    {
        SIndex sidx = oa.soid().sidx();
        SID sid = _sidx2sid.get_(sidx);


        List<Folder> folders = Lists.newArrayList();
        List<File> files = Lists.newArrayList();

        IDBIterator<OID> it = _ds.listChildren_(oa.soid());
        try {
            while (it.next_()) addChild(sidx, sid, it.get_(), parent, token, files, folders);
        } finally {
            it.close_();
        }

        // The spec says the items are to be sorted as a pre-requisite for pagination.
        //
        // Sort order of user-visible strings should be locale-aware. The naive approach of binary
        // comparison of unicode characters is good enough for a first private iteration but it is
        // going to be a problem for customers that do not stick to ASCII names.
        //
        // Using Collator for locale-aware comparison is fairly straightforward but in the not so
        // unlikely event of the client and server having different locales server-side sorting will
        // give wrong results. A possible solution to this would be to specify a locale (language
        // code?) in the request headers but it is outside the scope of the first iteration.
        Collections.sort(files, File.BY_NAME);
        Collections.sort(folders, Folder.BY_NAME);

        return new ChildrenList(includeParent ? parent : null, folders, files);
    }

    private void addChild(SIndex sidx, SID sid, OID c, String parent, OAuthToken token,
            List<File> files,  List<Folder> folders)
            throws ExNotFound, SQLException
    {
        OA coa = _ds.getOAThrows_(new SOID(sidx, c));
        String restId = new RestObject(sid, c).toStringFormal();
        if (coa.isFile()) {
            files.add(file(coa, restId, parent, null));
        } else if (shouldIncludeInResponse(coa, token)) {
            folders.add(folder(coa, restId, parent, null, null));
        }
    }

    private boolean shouldIncludeInResponse(OA oa, OAuthToken token)
            throws SQLException
    {
        // never  show the trash
        if (oa.soid().oid().isTrash()) return false;

        // If the OAuth token has the "linksharing" scope, anchor names should be included iff the
        // owner of the token is a manager of the store.
        if (!oa.isAnchor() || !token.hasPermission(Scope.LINKSHARE)) return true;
        SIndex sidx = _sid2sidx.getNullable_(SID.anchorOID2storeSID(oa.soid().oid()));
        return sidx != null && _acl.check_(token.user(), sidx, Permissions.allOf(Permission.MANAGE));
    }
}
