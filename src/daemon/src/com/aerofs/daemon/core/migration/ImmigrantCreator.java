package com.aerofs.daemon.core.migration;

import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.object.ObjectMover;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.lib.id.KIndex;
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
 */
public class ImmigrantCreator
{
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final ObjectCreator _oc;
    private final ObjectMover _om;
    private final ObjectDeleter _od;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public ImmigrantCreator(DirectoryService ds, IPhysicalStorage ps, IMapSIndex2SID sidx2sid,
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

    /**
     * This method either moves objects within the same store, or across stores via migration,
     * depending on whether the old sidx is the same as the new one.
     *
     * @return the SOID of the object after the move. This new SOID may be different from
     * the parameter {@code soid} if migration occurs.
     *
     * Note: This is a method operate at the topmost level. Putting it in ObjectMover would
     * introduce a circular dependency, which is why it lives in ImmigrantCreator instead.
     * This area of the code would benefit from a good helping of refactoring but now is not
     * the time...
     */
    public SOID move_(SOID soid, SOID soidToParent, String toName, PhysicalOp op, Trans t)
            throws Exception
    {
        if (soidToParent.sidx().equals(soid.sidx())) {
            _om.moveInSameStore_(soid, soidToParent.oid(), toName, op, false, true, t);
            return soid;
        } else {
            return createImmigrantRecursively_(_ds.resolve_(soid).parent(), soid, soidToParent,
                    toName, op, t);
        }
    }

    static class MigratedPath
    {
        public final ResolvedPath from;
        public final ResolvedPath to;

        MigratedPath(ResolvedPath from, ResolvedPath to)
        {
            this.from = from;
            this.to = to;
        }

        MigratedPath join(OA from, SOID to, String name)
        {
            return new MigratedPath(this.from.join(from), this.to.join(to, name));
        }
    }

    /**
     * Recursively migrate the object corresponding to {@code soidFromRoot} to
     * under {@code soidToRootParent}.
     *
     * This method assumes that permissions have been checked.
     *
     * @param soidFromRoot the SOID of the root object to be migrated
     * @param soidToRootParent the SOID of the parent to which the root object will be migrated
     * @return the new SOID of the root object
     */
    public SOID createImmigrantRecursively_(ResolvedPath pathFromParent, final SOID soidFromRoot,
            final SOID soidToRootParent, final String toRootName, final PhysicalOp op, final Trans t)
            throws ExStreamInvalid, IOException, ExNotFound, ExAlreadyExist, SQLException, ExNotDir
    {
        assert !soidFromRoot.sidx().equals(soidToRootParent.sidx());

        MigratedPath pathParent = new MigratedPath(
                pathFromParent,
                _ds.resolve_(soidToRootParent).substituteLastSOID(soidToRootParent));

        _ds.walk_(soidFromRoot, pathParent, new IObjectWalker<MigratedPath>() {
            @Override
            public MigratedPath prefixWalk_(MigratedPath pathParent, OA oaFrom)
                    throws SQLException, IOException, ExNotFound, ExAlreadyExist, ExNotDir,
                    ExStreamInvalid
            {
                // do not walk trash
                if (oaFrom.soid().oid().isTrash()) return null;

                // when walking across store boundary (i.e through an anchor), we do not need
                // to re-create the root dir in the destination, however we need to make sure
                // any physical trace of the former anchor disappears
                if (oaFrom.soid().oid().isRoot()) return pathParent;

                SOID soidToParent = pathParent.to.isEmpty()
                        ? soidToRootParent : pathParent.to.soid();
                SOID soidTo = new SOID(soidToParent.sidx(), migratedOID(oaFrom.soid().oid()));
                String name = soidFromRoot.equals(oaFrom.soid()) ? toRootName : oaFrom.name();

                // make sure the physical file reflect the migrated SOID before any MAP operation
                if (op == PhysicalOp.MAP) {
                    ResolvedPath pathFrom = pathParent.from.join(oaFrom);
                    if (oaFrom.isFile()) {
                        for (KIndex kidx : oaFrom.cas().keySet()) {
                            _ps.newFile_(pathFrom, kidx).updateSOID_(soidTo, t);
                        }
                    } else {
                        _ps.newFolder_(pathFrom).updateSOID_(soidTo, t);
                    }
                }

                OA oaToExisting = _ds.getOANullable_(soidTo);
                Type typeTo = migratedType(oaFrom.type());

                if (oaToExisting == null) {
                    int flags = oaFrom.flags() & ~FLAG_EXPELLED_ORG_OR_INH;
                    _oc.createImmigrantMeta_(typeTo, oaFrom.soid(), soidTo, soidToParent.oid(),
                            name, flags, op, true, t);
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
                    _ps.newFolder_(_ds.resolve_(soidTo))
                            .demoteToRegularFolder_(SID.anchorOID2storeSID(oaFrom.soid().oid()), op, t);
                }

                return pathParent.join(oaFrom, soidTo, name);
            }

            @Nullable SID _sid;

            @Override
            public void postfixWalk_(MigratedPath pathParent, OA oaFrom)
                    throws IOException, ExAlreadyExist, SQLException, ExNotDir, ExNotFound,
                    ExStreamInvalid
            {
                if (oaFrom.soid().oid().isRoot() || oaFrom.soid().oid().isTrash()) return;

                if (_sid == null) {
                    _sid = pathParent.to.isEmpty()
                            ? pathParent.to.sid()
                            : _sidx2sid.get_(pathParent.to.soid().sidx());
                }

                // The use and abuse of PhysicalOp in Aliasing and migration has been a major
                // source of grief when doing changes in the core. We really need to come up
                // with better semantics.
                // In this case we should not use MAP when deleting because this would try to
                // delete NROs/conflicts that don't actually exist as they've been renamed to
                // reflect the SOID change.
                PhysicalOp realOp = op == PhysicalOp.MAP ? PhysicalOp.NOP : op;

                if (oaFrom.isAnchor()) {
                    // NB: to properly leave the store we must not keep track of the emigration
                    _od.delete_(oaFrom.soid(), realOp, t);
                } else {
                    _od.deleteAndEmigrate_(oaFrom.soid(), realOp, _sid, t);
                }
            }
        });

        return new SOID(soidToRootParent.sidx(), soidFromRoot.oid());
    }
}
