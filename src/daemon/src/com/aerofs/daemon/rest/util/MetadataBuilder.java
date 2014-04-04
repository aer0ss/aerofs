/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.util;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.RestObject;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.ChildrenList;
import com.aerofs.rest.api.CommonMetadata;
import com.aerofs.rest.api.File;
import com.aerofs.rest.api.Folder;
import com.aerofs.rest.api.ParentPath;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class MetadataBuilder
{
    private final IStores _stores;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final MimeTypeDetector _detector;
    private final EntityTagUtil _etags;

    @Inject
    public MetadataBuilder(DirectoryService ds, IMapSIndex2SID sidx2sid, IStores stores,
            MimeTypeDetector detector, EntityTagUtil etags)
    {
        _ds = ds;
        _stores = stores;
        _sidx2sid = sidx2sid;
        _detector = detector;
        _etags = etags;
    }

    public RestObject object(SOID soid) throws ExNotFound
    {
        return new RestObject(_sidx2sid.getThrows_(soid.sidx()), soid.oid());
    }

    public CommonMetadata metadata(SOID soid, UserID user) throws ExNotFound, SQLException
    {
        return metadata(_ds.getOAThrows_(soid), user, null);
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
        Set<SIndex> parents = _stores.getParents_(soid.sidx());
        // NB: this won't work with nested sharing, which is okay because we don't support that...
        for (SIndex sidxParent : parents) {
            if (root.equals(_sidx2sid.get_(sidxParent))) {
                return new SOID(sidxParent, SID.storeSID2anchorOID(sid));
            }
        }
        return soid;
    }

    public CommonMetadata metadata(OA oa, UserID user, Fields fields) throws ExNotFound, SQLException
    {
        SOID soid = selfOrAnchorForUser(oa.soid(), user);
        if (!soid.equals(oa.soid())) oa = _ds.getOA_(soid);

        SID sid = _sidx2sid.get_(soid.sidx());
        RestObject object = new RestObject(sid, soid.oid());

        RestObject parent;
        if (oa.parent().isRoot()) {
            SOID soidParent = selfOrAnchorForUser(new SOID(soid.sidx(), oa.parent()), user);
            parent = new RestObject(_sidx2sid.get_(soidParent.sidx()), soidParent.oid());
        } else {
            parent = new RestObject(sid, oa.parent());
        }

        // TODO: handle external roots
        String name = soid.oid().isRoot() ? "AeroFS" : oa.name();
        CA ca = oa.isFile() ? oa.caMasterNullable() : null;
        Date mtime = ca != null ? new Date(ca.mtime()) : null;
        Long length = ca != null ? ca.length() : null;

        ParentPath path = fields != null && fields.isRequested("path")
                ? path(_ds.resolve_(oa), user) : null;
        ChildrenList children = null;
        if (oa.isDirOrAnchor() && fields != null && fields.isRequested("children")) {
            OA oaDir = oa;
            if (oa.isAnchor()) {
                SOID soidDir = _ds.followAnchorNullable_(oa);
                oaDir = soidDir != null ? _ds.getOANullable_(soidDir) : null;
            }
            if (oaDir != null) {
                children = children(object.toStringFormal(), oaDir, false);
            } else {
                children = new ChildrenList(null, Collections.<Folder>emptyList(),
                        Collections.<File>emptyList());
            }
        }

        return oa.isDirOrAnchor()
                ? new Folder(object.toStringFormal(), name, parent.toStringFormal(), path,
                oa.isAnchor() ? SID.anchorOID2storeSID(soid.oid()).toStringFormal() : null, children)
                : new File(object.toStringFormal(), name, parent.toStringFormal(), path, mtime, length,
                _detector.detect(name), _etags.etagForContent(soid).getValue());
    }


    public ParentPath path(ResolvedPath path, UserID user) throws ExNotFound, SQLException
    {
        // TODO: determine name for alternate roots (aka external shares)
        List<Folder> folders = Lists.newArrayList();
        if (!path.isEmpty()) {
            folders.add(new Folder(new RestObject(path.sid(), OID.ROOT).toStringFormal(),
                    path.sid().isUserRoot() ? "AeroFS" :  "",
                    path.sid().isUserRoot() ? null : path.sid().toStringFormal()));
        }

        for (int i = 0; i < path.soids.size() - 1; ++i) {
            folders.add((Folder)metadata(path.soids.get(i), user));
        }

        return new ParentPath(folders);
    }

    public ChildrenList children(String parent, OA oa, boolean includeParent)
            throws ExNotFound, SQLException
    {
        SIndex sidx = oa.soid().sidx();
        SID sid = _sidx2sid.get_(sidx);
        Collection<OID> children;
        try {
            children = _ds.getChildren_(oa.soid());
        } catch (ExNotDir e) { throw new ExNotFound(e.getMessage()); }

        List<Folder> folders = Lists.newArrayList();
        List<File> files = Lists.newArrayList();
        for (OID c : children) {
            OA coa = _ds.getOAThrows_(new SOID(sidx, c));
            String restId = new RestObject(sid, c).toStringFormal();
            if (coa.isFile()) {
                Long size = null;
                Date lastModified = null;
                if (!coa.isExpelled() && coa.caMasterNullable() != null) {
                    size = coa.caMaster().length();
                    lastModified = new Date(coa.caMaster().mtime());
                }
                files.add(new File(restId, coa.name(), parent,
                        lastModified, size, _detector.detect(coa.name()),
                        _etags.etagForContent(coa.soid()).getValue()));
            } else if (!coa.soid().oid().isTrash()){
                folders.add(new Folder(restId, coa.name(), parent, coa.isAnchor()
                        ? SID.anchorOID2storeSID(coa.soid().oid()).toStringFormal() : null));
            }
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
}
