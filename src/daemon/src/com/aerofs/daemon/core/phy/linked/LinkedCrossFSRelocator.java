/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.daemon.core.admin.HdRelocateRootAnchor.CrossFSRelocator;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Implementation of {@link com.aerofs.daemon.core.admin.HdRelocateRootAnchor.CrossFSRelocator}
 * that updates FID of all relocated objects
 */
class LinkedCrossFSRelocator extends CrossFSRelocator
{
    private final LinkerRootMap _lrm;

    @Inject
    public LinkedCrossFSRelocator(InjectableFile.Factory factFile,
            DirectoryService ds, InjectableDriver dr, LinkerRootMap lrm)
    {
        super(factFile, ds, dr);
        _lrm = lrm;
    }

    @Override
    public void afterRootRelocation(Trans t) throws Exception
    {
        updateFID(_ds.resolveThrows_(Path.root(_sid)),
                _newRoot.getAbsolutePath(), t);

        _lrm.move_(_sid, _oldRoot.getAbsolutePath(), _newRoot.getAbsolutePath(), t);
    }

    private void updateFID(SOID newRootSOID, String absNewRoot, final Trans t) throws Exception
    {
        // Initial walk inserts extra byte to each new FID to avoid duplicate key conflicts in
        // the database in case some files in the new location happen to have identical FIDs
        // with original files -- although it should be very rare. Second walk removes this
        // extra byte.

        _ds.walk_(newRootSOID, absNewRoot, new IObjectWalker<String>() {
            @Override
            public String prefixWalk_(String oldParent, OA oa)
            {
                return prefixWalk(oldParent, oa);
            }

            @Override
            public void postfixWalk_(String oldParent, OA oa)
                    throws IOException, SQLException
            {
                // Root objects have null FIDs
                if (!oa.soid().oid().isRoot() && oa.fid() != null) {
                    FID newFID = _dr.getFID(oldParent + oa.name());

                    // newFID can be null if it is removed by filesystem during relocation
                    if (newFID == null) {
                        newFID = oa.fid();
                        assert newFID != null : oa;
                    }

                    byte[] bytesFID = Arrays.copyOf(newFID.getBytes(), _dr.getFIDLength() + 1);
                    _ds.setFID_(oa.soid(), new FID(bytesFID), t);
                }
            }
        });

        _ds.walk_(newRootSOID, absNewRoot, new IObjectWalker<String>() {
            @Override
            public String prefixWalk_(String oldParent, OA oa)
            {
                return prefixWalk(oldParent, oa);
            }

            @Override
            public void postfixWalk_(String oldParent, OA oa)
                    throws IOException, SQLException {
                if (!oa.soid().oid().isRoot() && oa.fid() != null) {
                    assert oa.fid().getBytes().length == _dr.getFIDLength() + 1;

                    byte[] bytesFID = Arrays.copyOf(oa.fid().getBytes(), _dr.getFIDLength());
                    _ds.setFID_(oa.soid(), new FID(bytesFID), t);
                }
            }
        });
    }

    /**
     * oldParent has the File.separator affixed to the end of the path (except root).
     */
    private @Nullable String prefixWalk(String oldParent, OA oa)
    {
        if (oa.isExpelled()) {
            assert oa.fid() == null;
            return null;
        }

        switch (oa.type()) {
        case ANCHOR: return oldParent + oa.name();
        case DIR:    return (oa.soid().oid().isRoot()) ? oldParent + File.separator
                : oldParent + oa.name() + File.separator;
        case FILE:   return null;
        default:     assert false; return null;
        }
    }
}
