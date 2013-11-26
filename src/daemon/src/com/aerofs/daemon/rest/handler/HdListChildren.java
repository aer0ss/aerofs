package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.daemon.rest.util.MimeTypeDetector;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.ChildrenList;
import com.aerofs.rest.api.File;
import com.aerofs.rest.api.Folder;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class HdListChildren extends AbstractHdIMC<EIListChildren>
{
    private final RestObjectResolver _access;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final MimeTypeDetector _detector;

    @Inject
    public HdListChildren(RestObjectResolver access, DirectoryService ds, IMapSIndex2SID sidx2sid,
            MimeTypeDetector detector)
    {
        _access = access;
        _ds = ds;
        _sidx2sid = sidx2sid;
        _detector = detector;
    }

    @Override
    protected void handleThrows_(EIListChildren ev, Prio prio) throws ExNotFound, SQLException
    {
        OA oa = _access.resolveFollowsAnchor_(ev._object, ev._user);

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
            if (coa.isExpelled()) continue;
            String restId = new RestObject(sid, c).toStringFormal();
            if (coa.isFile()) {
                long size = -1;
                Date lastModified = null;
                if (coa.caMasterNullable() != null) {
                    size = coa.caMaster().length();
                    lastModified = new Date(coa.caMaster().mtime());
                }
                files.add(new File(restId, coa.name(), lastModified, size,
                        _detector.detect(coa.name())));
            } else {
                folders.add(new Folder(restId, coa.name(), coa.isAnchor()));
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

        ev.setResult_(new ChildrenList(ev._object.toStringFormal(), folders, files));
    }
}
