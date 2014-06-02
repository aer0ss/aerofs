/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.linked;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.admin.HdRelocateRootAnchor.CrossFSRelocator;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.linked.RepresentabilityHelper.PathType;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRoot;
import com.aerofs.daemon.core.phy.linked.linker.LinkerRootMap;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Implementation of {@link com.aerofs.daemon.core.admin.HdRelocateRootAnchor.CrossFSRelocator}
 * that updates FID of all relocated objects
 */
class LinkedCrossFSRelocator extends CrossFSRelocator
{
    private final static Logger l = Loggers.getLogger(LinkedCrossFSRelocator.class);

    private final LinkerRootMap _lrm;
    private final FileSystemProber _prober;
    private final RepresentabilityHelper _rh;

    @Inject
    public LinkedCrossFSRelocator(InjectableFile.Factory factFile, FileSystemProber prober,
            DirectoryService ds, InjectableDriver dr, LinkerRootMap lrm, RepresentabilityHelper rh)
    {
        super(factFile, ds, dr);
        _lrm = lrm;
        _prober = prober;
        _rh = rh;
    }

    @Override
    protected void beforeRootRelocation(Trans t) throws Exception
    {
        // special combined move, strictness check not practical
        if (L.isMultiuser() && _sid.equals(Cfg.rootSID())) return;

        if (_prober.isStricterThan(_newAuxRoot.getAbsolutePath(), _lrm.get_(_sid).properties())) {
            throw new ExBadArgs("The target location is on a more restrictive filesystem than the source location.");
        }
    }

    @Override
    public void afterRootRelocation(Trans t) throws Exception
    {
        // update linker root first, to be able to use RepresentabilityHelper in updateFID
        _lrm.move_(_sid, _oldRoot.getAbsolutePath(), _newRoot.getAbsolutePath(), t);

        if (L.isMultiuser() && _sid.equals(Cfg.rootSID())) {
            for (LinkerRoot lr : _lrm.getAllRoots_()) updateFID(lr.sid(), t);
        } else {
            updateFID(_sid, t);
        }
    }

    private void updateFID(SID sid, final Trans t) throws Exception
    {
        // Initial walk inserts extra byte to each new FID to avoid duplicate key conflicts in
        // the database in case some files in the new location happen to have identical FIDs
        // with original files -- although it should be very rare. Second walk removes this
        // extra byte.

        ResolvedPath p = ResolvedPath.root(sid);
        SOID soid =_ds.resolveThrows_(p);
        _ds.walk_(soid, p, new IObjectWalker<ResolvedPath>() {
            @Override
            public ResolvedPath prefixWalk_(ResolvedPath oldParent, OA oa) throws SQLException
            {
                return prefixWalk(oldParent, oa);
            }

            @Override
            public void postfixWalk_(ResolvedPath oldParent, OA oa)
                    throws IOException, SQLException
            {
                // Root objects have null FIDs
                if (!oa.soid().oid().isRoot() && oa.fid() != null) {
                    ResolvedPath path = oldParent.join(oa);
                    LinkedPath lp = _rh.getPhysicalPath_(path, PathType.SOURCE);
                    FID newFID = null;
                    try {
                        newFID = _dr.getFID(lp.physical);
                    } catch (Exception e) {
                        l.warn("could not get fid for {}", lp.physical, BaseLogUtil.suppress(e));
                    }

                    if (newFID == null) {
                        // could not get FID for any reason
                        // the actual relocation went fine so aborting and rolling back now
                        // would be a pretty terrible thing to do. Assign a random FID that
                        // is guaranteed not to conflict with any real FID and let the scanner
                        // resolve things
                        newFID = new FID(UniqueID.generate().getBytes());
                    }

                    byte[] bytesFID = Arrays.copyOf(newFID.getBytes(), _dr.getFIDLength() + 1);
                    l.info("update fid {} {} {}", oa.soid(), oa.fid(), newFID);
                    _ds.setFID_(oa.soid(), new FID(bytesFID), t);
                }
            }
        });

        _ds.walk_(soid, p, new IObjectWalker<ResolvedPath>() {
            @Override
            public ResolvedPath prefixWalk_(ResolvedPath oldParent, OA oa) throws SQLException
            {
                return prefixWalk(oldParent, oa);
            }

            @Override
            public void postfixWalk_(ResolvedPath oldParent, OA oa)
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
    private @Nullable ResolvedPath prefixWalk(ResolvedPath oldParent, OA oa) throws SQLException
    {
        if (oa.isExpelled()) {
            assert oa.fid() == null;
            return null;
        }

        switch (oa.type()) {
        case ANCHOR: return oldParent.join(oa);
        case DIR:    return (oa.soid().oid().isRoot()) ? oldParent: oldParent.join(oa);
        case FILE:   return null;
        default:     assert false; return null;
        }
    }
}
