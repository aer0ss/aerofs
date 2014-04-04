package com.aerofs.daemon.core.expel;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.ObjectWalkerAdapter;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion.IExpulsionListener;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import static com.google.common.base.Preconditions.checkArgument;

class ExpelledToAdmittedAdjuster implements IExpulsionAdjuster
{
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final ImmigrantDetector _imd;
    private final Expulsion _expulsion;
    private final StoreCreator _sc;

    @Inject
    public ExpelledToAdmittedAdjuster(StoreCreator sc, Expulsion expulsion, ImmigrantDetector imd,
            DirectoryService ds, IPhysicalStorage ps)
    {
        _sc = sc;
        _expulsion = expulsion;
        _imd = imd;
        _ds = ds;
        _ps = ps;
    }

    @Override
    public void adjust_(ResolvedPath pathOld, final SOID soidRoot,
            boolean emigrate, final PhysicalOp op, final Trans t)
            throws Exception
    {
        checkArgument(!emigrate);

        // must recreate the *new* tree
        // otherwise we may try to create physical object under the trash folder
        // which would fail and result in the apparition of nro which would be
        // mightily confusing
        ResolvedPath p = _ds.resolve_(soidRoot);

        _ds.walk_(soidRoot, p, new ObjectWalkerAdapter<ResolvedPath>() {
            @Override
            public ResolvedPath prefixWalk_(ResolvedPath parentPath, OA oa)
                    throws Exception {
                boolean isRoot = soidRoot.equals(oa.soid());

                ResolvedPath path = isRoot ? parentPath : parentPath.join(oa);

                // skip the current node and its children if the effective state of the current
                // object doesn't change
                if (oa.isSelfExpelled()) return null;

                for (IExpulsionListener l : _expulsion.listeners_()) {
                    l.objectAdmitted_(oa.soid(), t);
                }

                switch (oa.type()) {
                case FILE:
                    _imd.detectAndPerformImmigration_(oa, op, t);
                    _expulsion.fileAdmitted_(oa.soid(), t);
                    return null;
                case DIR:
                    _ps.newFolder_(path).create_(op, t);
                    return path;
                case ANCHOR:
                    boolean immigrated = _imd.detectAndPerformImmigration_(oa, op, t);
                    if (!immigrated) {
                        SID sid = SID.anchorOID2storeSID(oa.soid().oid());
                        IPhysicalFolder pf = _ps.newFolder_(path);
                        pf.create_(op, t);
                        _sc.addParentStoreReference_(sid, oa.soid().sidx(), oa.name(), t);
                        pf.promoteToAnchor_(sid, op, t);
                    }
                    return null;
                default:
                    assert false;
                    return null;
                }
            }
        });
    }
}
