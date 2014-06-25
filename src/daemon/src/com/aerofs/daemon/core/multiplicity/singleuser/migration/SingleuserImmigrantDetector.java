/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import java.io.IOException;
import java.sql.SQLException;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.google.inject.Inject;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.base.id.SID;

import javax.annotation.Nonnull;

/**
 * For backward compat, singleuser systems support remotely-initiated migration of anchors even
 * though locally-initiated migration now demotes anchor back to regular folders to prevent the
 * creation of nested shared folders.
 */
public class SingleuserImmigrantDetector extends ImmigrantDetector
{
    private ObjectDeleter _od;
    private IMapSID2SIndex _sid2sidx;
    private IMapSIndex2SID _sidx2sid;
    private AdmittedObjectLocator _aol;
    private IStores _ss;

    @Inject
    public void inject_(DirectoryService ds, NativeVersionControl nvc, ImmigrantVersionControl ivc,
            IPhysicalStorage ps, ObjectDeleter od, IMapSID2SIndex sid2sidx,
            AdmittedObjectLocator aol, IMapSIndex2SID sidx2sid, IStores ss)
    {
        baseInject_(ds, nvc, ivc, ps);
        _ss = ss;
        _sid2sidx = sid2sidx;
        _od = od;
        _aol = aol;
        _sidx2sid = sidx2sid;
    }

    @Override
    public boolean detectAndPerformImmigration_(@Nonnull OA oaTo, PhysicalOp op, Trans t)
            throws Exception
    {
        /** assert for assumption 1) in {@link ImmigrantDetector#detectAndPerformImmigration_} */
        assert !oaTo.isExpelled();
        /** assert for assumption 4) in {@link ImmigrantDetector#detectAndPerformImmigration_} */
        assert !oaTo.isFile() || oaTo.cas().isEmpty();

        // directories can't be migrated
        if (oaTo.isDir()) return false;

        OA oaFrom = _aol.locate_(oaTo.soid().oid(), oaTo.soid().sidx(), oaTo.type());
        if (oaFrom == null) return false;

        assert oaFrom.type() == oaTo.type();

        if (oaFrom.isFile()) {
            immigrateFile_(oaFrom, oaTo, op, t);
        } else {
            // guaranteed by the above code
            assert oaFrom.isAnchor();
            immigrateAnchor_(oaFrom, oaTo, op, t);
        }

        SID sid = _sidx2sid.get_(oaTo.soid().sidx());
        // NB: the immigrate* methods have already moved the physical object, hence the NOP
        _od.deleteAndEmigrate_(oaFrom.soid(), PhysicalOp.NOP, sid, t);

        // TODO notify with a MOVE or DELETE+CREATE event?

        return true;
    }

    private void immigrateAnchor_(OA oaFrom, OA oaTo, PhysicalOp op, Trans t)
            throws IOException, ExAlreadyExist, ExNotFound, ExNotDir, SQLException
    {
        assert !oaFrom.soid().sidx().equals(oaTo.soid().sidx());
        assert oaFrom.soid().oid().equals(oaTo.soid().oid());
        assert oaFrom.isAnchor() && oaTo.isAnchor();
        assert !oaFrom.isExpelled() && !oaTo.isExpelled();

        SOID soidTo = oaTo.soid();

        SID sid = SID.anchorOID2storeSID(soidTo.oid());
        SIndex sidx = _sid2sidx.get_(sid);

        // update the child store's parents
        _ss.deleteParent_(sidx, oaFrom.soid().sidx(), t);
        _ss.addParent_(sidx, soidTo.sidx(), t);

        // move physical objects
        _ps.newFolder_(_ds.resolve_(oaFrom)).move_(_ds.resolve_(oaTo), op, t);
    }
}
