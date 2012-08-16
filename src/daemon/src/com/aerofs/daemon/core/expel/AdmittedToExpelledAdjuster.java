package com.aerofs.daemon.core.expel;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nullable;

public class AdmittedToExpelledAdjuster implements IExpulsionAdjuster
{
    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final IPhysicalStorage _ps;
    private final Expulsion _expulsion;
    private final StoreDeleter _sd;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public AdmittedToExpelledAdjuster(StoreDeleter sd, Expulsion expulsion,
            IPhysicalStorage ps, NativeVersionControl nvc, DirectoryService ds,
            IMapSID2SIndex sid2sidx)
    {
        _sd = sd;
        _expulsion = expulsion;
        _ps = ps;
        _nvc = nvc;
        _ds = ds;
        _sid2sidx = sid2sidx;
    }

    @Override
    public void adjust_(final boolean emigrate, final PhysicalOp op, final SOID soidRoot, Path pOld,
            final int flagsRoot, final Trans t) throws Exception
    {
        _ds.walk_(soidRoot, pOld, new IObjectWalker<Path>() {
            @Override
            public @Nullable Path prefixWalk_(Path pOldParent, OA oa) throws Exception
            {
                boolean isRoot = soidRoot.equals(oa.soid());
                boolean oldExpelled = oa.isExpelled();

                Path pathOld = isRoot ? pOldParent : pOldParent.append(oa.name());

                switch (oa.type()) {
                case FILE:

                    // because users can't expel non-folder files, and because the walk doesn't
                    // enter a folder if the folder has been expelled (see the DIR case), we should
                    // never walk on a file which has been expelled before.
                    assert !oldExpelled;

                    SOCID socid = new SOCID(oa.soid(), CID.CONTENT);
                    Version vKMLAdd = new Version();
                    for (KIndex kidx : oa.cas(false).keySet()) {
                        SOCKID k = new SOCKID(socid, kidx);
                        if (!emigrate) _ps.newFile_(k.sokid(), pathOld).delete_(op, t);
                        Version vBranch = _nvc.getLocalVersion_(k);
                        vKMLAdd = vKMLAdd.add_(vBranch);
                        _nvc.deleteLocalVersion_(k, vBranch, t);
                        _ds.deleteCA_(oa.soid(), kidx, t);
                    }

                    // move all the local versions to KML version
                    Version vKMLOld = _nvc.getKMLVersion_(socid);
                    _nvc.addKMLVersionNoAssert_(socid, vKMLAdd.sub_(vKMLOld), t);

                    _expulsion.fileExpelled_(oa.soid());
                    return null;

                case DIR:
                    // skip children if the expulsion state of the current object doesn't change
                    return oldExpelled ? null : pathOld;

                case ANCHOR:
                    if (!oldExpelled && !emigrate) {
                        // delete the anchored store
                        SIndex sidx = _sid2sidx.get_(SID.anchorOID2storeSID(oa.soid().oid()));
                        _sd.deleteRecursively_(sidx, pathOld, op, t);
                        _ps.newFolder_(oa.soid(), pathOld).delete_(op, t);
                    }
                    return null;

                default:
                    assert false;
                    return null;
                }
            }

            @Override
            public void postfixWalk_(Path pOldParent, OA oa) throws Exception
            {
                boolean isRoot = soidRoot.equals(oa.soid());

                if (!oa.isExpelled() && oa.isDir()) {
                    Path pathOld = isRoot ? pOldParent : pOldParent.append(oa.name());
                    // have to do it in postfixWalk rather than prefixWalk because otherwise child
                    // objects under the folder would prevent us from deleting the folder.
                    _ps.newFolder_(oa.soid(), pathOld).delete_(op, t);
                }

                // set the flags _after_ physically deleting folders, since physical file
                // implementations may assume that the corresponding logical object has not been
                // expelled when deleting the physical object.
                // see also ExpelledToAdmittedAdjuster
                int flagsNew = isRoot ? flagsRoot : Util.set(oa.flags(), OA.FLAG_EXPELLED_INH);
                _ds.setOAFlags_(oa.soid(), flagsNew, t);
            }
        });
    }
}
