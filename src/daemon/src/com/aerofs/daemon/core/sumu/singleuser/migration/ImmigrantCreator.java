package com.aerofs.daemon.core.sumu.singleuser.migration;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.daemon.core.ds.OA.FLAG_EXPELLED_ORG_OR_INH;

/**
 * This class operates at a higher level than ObjectMover/Deleter/Creator, which in turn are at
 * a higher level than ImmigrantDetector. That is, they have the dependency of:
 *
 * ImmigrantCreator -> ObjectMover/Deleter/Creator -> ImmigrantDetector
 *
 */
public class ImmigrantCreator implements IImmigrantCreator
{
    private DirectoryService _ds;
    private ObjectCreator _oc;
    private ObjectMover _om;
    private ObjectDeleter _od;
    private IMapSIndex2SID _sidx2sid;

    @Inject
    public void inject_(DirectoryService ds, IMapSIndex2SID sidx2sid, ObjectMover om,
            ObjectDeleter od, ObjectCreator oc)
    {
        _ds = ds;
        _sidx2sid = sidx2sid;
        _om = om;
        _od = od;
        _oc = oc;
    }

    @Override
    public SOID createImmigrantRecursively_(final SOID soidFromRoot, SOID soidToRootParent,
            final String toRootName, final PhysicalOp op, final Trans t)
            throws ExStreamInvalid, IOException, ExNotFound, ExAlreadyExist, SQLException, ExNotDir
    {
        assert !soidFromRoot.sidx().equals(soidToRootParent.sidx());

        _ds.walk_(soidFromRoot, soidToRootParent, new IObjectWalker<SOID>() {
            @Override
            public SOID prefixWalk_(SOID soidToParent, OA oaFrom)
                    throws SQLException, IOException, ExNotFound, ExAlreadyExist, ExNotDir,
                    ExStreamInvalid
            {
                SOID soidTo = new SOID(soidToParent.sidx(), oaFrom.soid().oid());
                String name = soidFromRoot.equals(oaFrom.soid()) ? toRootName : oaFrom.name();

                OA oaToExisting = _ds.getOANullable_(soidTo);

                if (oaToExisting == null) {
                    int flags = oaFrom.flags() & ~FLAG_EXPELLED_ORG_OR_INH;
                    _oc.createMeta_(oaFrom.type(), soidTo, soidToParent.oid(), name, flags,
                            op, true, true, t);
                } else {
                    // Comment (B)
                    //
                    // It's an invariant that at any given time, among all the
                    // objects sharing the same OID in the local system, at
                    // most one of them is admitted, this is guaranteed by the
                    // implementation. See the invariant in AdmittedObjectLocator.
                    assert oaFrom.type() == oaToExisting.type();
                    assert oaFrom.isExpelled() || oaToExisting.isExpelled();
                    _om.moveInSameStore_(soidTo, soidToParent.oid(), name, op, false, true, t);
                }

                return oaFrom.isAnchor() ? null : soidTo;
            }

            SID _sid;

            @Override
            public void postfixWalk_(SOID soidToParent, OA oaFrom)
                    throws IOException, ExAlreadyExist, SQLException, ExNotDir, ExNotFound,
                    ExStreamInvalid
            {
                if (oaFrom.isDir()) {
                    // directories aren't migrated. so delete them manually
                    if (_sid == null) _sid = _sidx2sid.get_(soidToParent.sidx());
                    _od.delete_(oaFrom.soid(), op, _sid, t);
                }
            }
        });

        return new SOID(soidToRootParent.sidx(), soidFromRoot.oid());
    }
}
