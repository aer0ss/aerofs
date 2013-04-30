package com.aerofs.daemon.core.expel;

import static com.aerofs.daemon.core.expel.Expulsion.effectivelyExpelled;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.ObjectWalkerAdapter;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

class ExpelledToAdmittedAdjuster implements IExpulsionAdjuster
{
    private final DirectoryService _ds;
    private final ImmigrantDetector _imd;
    private final Expulsion _expulsion;
    private final StoreCreator _sc;

    @Inject
    public ExpelledToAdmittedAdjuster(StoreCreator sc, Expulsion expulsion, ImmigrantDetector imd,
            DirectoryService ds)
    {
        _sc = sc;
        _expulsion = expulsion;
        _imd = imd;
        _ds = ds;
    }

    @Override
    public void adjust_(boolean emigrate, final PhysicalOp op, final SOID soidRoot, Path pOld,
            final int flagsRoot, final Trans t)
            throws IOException, ExNotFound, SQLException, ExStreamInvalid, ExAlreadyExist,
            ExNotDir
    {
        assert !emigrate;

        _ds.walk_(soidRoot, null, new ObjectWalkerAdapter<SOID>() {
            @Override
            public SOID prefixWalk_(SOID unused, OA oa)
                    throws SQLException, IOException, ExNotFound, ExAlreadyExist, ExNotDir,
                    ExStreamInvalid {
                boolean isRoot = soidRoot.equals(oa.soid());

                // set the flags _before_ physically creating folders, since physical file
                // implementations may assume that the corresponding logical object has been
                // admitted when creating the physical object.
                // see also AdmittedToExpelledAdjuster
                int flagsNew = isRoot ? flagsRoot : Util.unset(oa.flags(), OA.FLAG_EXPELLED_INH);
                _ds.setOAFlags_(oa.soid(), flagsNew, t);

                // skip the current node and its children if the effective state of the current
                // object doesn't change
                if (effectivelyExpelled(flagsNew)) return null;

                switch (oa.type()) {
                case FILE:
                    _imd.detectAndPerformImmigration_(oa, op, t);
                    _expulsion.fileAdmitted_(oa.soid(), t);
                    return null;
                case DIR:
                    oa.physicalFolder().create_(op, t);
                    return oa.soid();
                case ANCHOR:
                    boolean immigrated = _imd.detectAndPerformImmigration_(oa, op, t);
                    if (!immigrated) {
                        IPhysicalFolder pf = oa.physicalFolder();
                        pf.create_(op, t);
                        _sc.addParentStoreReference_(SID.anchorOID2storeSID(oa.soid().oid()),
                                oa.soid().sidx(), oa.name(), t);
                        pf.promoteToAnchor_(op, t);
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
