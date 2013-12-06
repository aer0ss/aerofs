/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.util;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.CommonMetadata;
import com.aerofs.rest.api.File;
import com.aerofs.rest.api.Folder;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Date;

public class MetadataBuilder
{
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final MimeTypeDetector _detector;

    @Inject
    public MetadataBuilder(DirectoryService ds, IMapSIndex2SID sidx2sid, MimeTypeDetector detector)
    {
        _ds = ds;
        _sidx2sid = sidx2sid;
        _detector = detector;
    }

    public CommonMetadata metadata(SOID soid) throws ExNotFound, SQLException
    {
        return metadata(_ds.getOAThrows_(soid));
    }

    public CommonMetadata metadata(OA oa) throws ExNotFound, SQLException
    {
        RestObject object = new RestObject(_sidx2sid.get_(oa.soid().sidx()), oa.soid().oid());

        String name = oa.name();
        CA ca = oa.isFile() && !oa.isExpelled() ? oa.caMasterNullable() : null;
        Date mtime = ca != null ? new Date(ca.mtime()) : null;
        Long length = ca != null ? ca.length() : null;

        return oa.isDirOrAnchor()
                ? new Folder(object.toStringFormal(), name, oa.isAnchor())
                : new File(object.toStringFormal(), name, mtime, length, _detector.detect(name));
    }
}
