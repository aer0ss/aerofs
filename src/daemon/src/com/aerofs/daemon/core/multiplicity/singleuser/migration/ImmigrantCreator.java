package com.aerofs.daemon.core.multiplicity.singleuser.migration;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.migration.IImmigrantCreator;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nullable;
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
    private IPhysicalStorage _ps;
    private ObjectCreator _oc;
    private ObjectMover _om;
    private ObjectDeleter _od;
    private IMapSIndex2SID _sidx2sid;

    @Inject
    public void inject_(DirectoryService ds, IPhysicalStorage ps, IMapSIndex2SID sidx2sid,
            ObjectMover om, ObjectDeleter od, ObjectCreator oc)
    {
        _ds = ds;
        _ps = ps;
        _sidx2sid = sidx2sid;
        _om = om;
        _od = od;
        _oc = oc;
    }

    /**
     * Important:
     *
     * To prevent the users from creating nested shares by moving anchors inside non-root stores
     * we special-case the migration of anchors: they are converted back to regular folders
     *
     * NB: This is the same behavior as Dropbox so ww is super happy
     */
    private static Type migratedType(Type t)
    {
        return t == Type.ANCHOR ? Type.DIR : t;
    }

    /**
     * When a shared folder is converted back to a regular folder we need to generate a new OID
     * to break the link between the two objects. This is necessary to avoid all sorts of nasty
     * coupling. For instance, reusing the original folder OID would prevent the new folder from
     * being re-shared later.
     */
    private static OID migratedOID(OID oid)
    {
        return oid.isAnchor() ? new OID(UniqueID.generate()) : oid;
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
                // do not walk trash
                if (oaFrom.soid().oid().isTrash()) return null;

                // when walking across store boundary (i.e through an anchor), we do not need
                // to re-create the root dir in the destination, however we need to make sure
                // any physical trace of the former anchor disappears
                if (oaFrom.soid().oid().isRoot()) return soidToParent;

                SOID soidTo = new SOID(soidToParent.sidx(), migratedOID(oaFrom.soid().oid()));
                String name = soidFromRoot.equals(oaFrom.soid()) ? toRootName : oaFrom.name();

                OA oaToExisting = _ds.getOANullable_(soidTo);
                Type typeTo = migratedType(oaFrom.type());

                if (oaToExisting == null) {
                    int flags = oaFrom.flags() & ~FLAG_EXPELLED_ORG_OR_INH;
                    _oc.createMeta_(typeTo, soidTo, soidToParent.oid(), name,
                            flags, op, true, true, t);
                } else {
                    // Comment (B)
                    //
                    // It's an invariant that at any given time, among all the
                    // objects sharing the same OID in the local system, at
                    // most one of them is admitted, this is guaranteed by the
                    // implementation. See the invariant in AdmittedObjectLocator.
                    assert typeTo == oaToExisting.type();
                    assert oaFrom.isExpelled() || oaToExisting.isExpelled() :
                            oaFrom + " " + oaToExisting;
                    _om.moveInSameStore_(soidTo, soidToParent.oid(), name, op, false, true, t);
                }

                // remove the tag file from the destination to gracefully handle both MAP and APPLY
                if (oaFrom.isAnchor()) {
                    // the IPhysicalFolder needs to be created with the anchor OID
                    // but we cannot simply reuse that of the old OA because it probably does not
                    // point to the correct path...
                    _ps.newFolder_(oaFrom.soid(), _ds.resolve_(soidTo))
                            .demoteToRegularFolder_(op, t);
                }

                return soidTo;
            }

            @Nullable SID _sid;

            @Override
            public void postfixWalk_(SOID soidToParent, OA oaFrom)
                    throws IOException, ExAlreadyExist, SQLException, ExNotDir, ExNotFound,
                    ExStreamInvalid
            {
                if (oaFrom.soid().oid().isRoot() || oaFrom.soid().oid().isTrash()) return;

                if (oaFrom.isDirOrAnchor()) {
                    // anchors are converted back to regular directories during migration and
                    // directories aren't migrated. so delete them manually
                    if (_sid == null) _sid = _sidx2sid.get_(soidToParent.sidx());
                    if (oaFrom.isAnchor()) {
                        // NB: to properly leave the store we must not keep track of the emigration
                        _od.delete_(oaFrom.soid(), op, t);
                    } else {
                        _od.deleteAndEmigrate_(oaFrom.soid(), op, _sid, t);
                    }

                }
            }
        });

        return new SOID(soidToRootParent.sidx(), soidFromRoot.oid());
    }
}
