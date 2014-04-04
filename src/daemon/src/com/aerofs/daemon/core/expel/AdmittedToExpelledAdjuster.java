package com.aerofs.daemon.core.expel;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion.IExpulsionListener;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.protocol.PrefixVersionControl;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;

public class AdmittedToExpelledAdjuster implements IExpulsionAdjuster
{
    private static final Logger l = Loggers.getLogger(AdmittedToExpelledAdjuster.class);

    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final PrefixVersionControl _pvc;
    private final IPhysicalStorage _ps;
    private final Expulsion _expulsion;
    private final StoreDeleter _sd;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public AdmittedToExpelledAdjuster(StoreDeleter sd, Expulsion expulsion,
            IPhysicalStorage ps, NativeVersionControl nvc, PrefixVersionControl pvc,
            DirectoryService ds, IMapSID2SIndex sid2sidx)
    {
        _sd = sd;
        _expulsion = expulsion;
        _ps = ps;
        _nvc = nvc;
        _pvc = pvc;
        _ds = ds;
        _sid2sidx = sid2sidx;
    }

    @Override
    public void adjust_(ResolvedPath pOld, final SOID soidRoot,
            final boolean emigrate, final PhysicalOp op, final Trans t)
            throws Exception
    {
        l.info("adm->exp {} {} {}", soidRoot, pOld, op);
        _ds.walk_(soidRoot, pOld, new IObjectWalker<ResolvedPath>() {
            @Override
            public @Nullable ResolvedPath prefixWalk_(ResolvedPath pOldParent, OA oa)
                    throws Exception
            {
                boolean isRoot = soidRoot.equals(oa.soid());
                boolean oldExpelled = !isRoot && oa.isSelfExpelled();

                ResolvedPath pathOld = isRoot ? pOldParent : pOldParent.join(oa);

                switch (oa.type()) {
                case FILE:

                    // because users can't expel non-folder files, and because the walk doesn't
                    // enter a folder if the folder has been expelled (see the DIR case), we should
                    // never walk on a file which has been expelled before.
                    assert !oldExpelled;

                    SOCID socid = new SOCID(oa.soid(), CID.CONTENT);
                    Version vKMLAdd = Version.empty();
                    for (KIndex kidx : oa.cas().keySet()) {
                        SOCKID k = new SOCKID(socid, kidx);
                        if (!emigrate) _ps.newFile_(pathOld, kidx).delete_(op, t);
                        Version vBranch = _nvc.getLocalVersion_(k);
                        vKMLAdd = vKMLAdd.add_(vBranch);
                        _nvc.deleteLocalVersion_(k, vBranch, t);

                        _ds.deleteCA_(oa.soid(), kidx, t);
                        _ps.deletePrefix_(k.sokid());
                    }

                    // move all the local versions to KML version
                    Version vKMLOld = _nvc.getKMLVersion_(socid);
                    _nvc.addKMLVersionNoAssert_(socid, vKMLAdd.sub_(vKMLOld), t);

                    _pvc.deleteAllPrefixVersions_(socid.soid(), t);

                    _expulsion.fileExpelled_(oa.soid());
                    return null;

                case DIR:
                    // skip children if the expulsion state of the current object doesn't change
                    return oldExpelled ? null : pathOld;

                case ANCHOR:
                    if (!oldExpelled && !emigrate) {
                        // delete the anchored store
                        SIndex sidx = _sid2sidx.get_(SID.anchorOID2storeSID(oa.soid().oid()));
                        _sd.removeParentStoreReference_(sidx, oa.soid().sidx(), pathOld, op, t);
                        _ps.newFolder_(pathOld).delete_(op, t);
                    }
                    return null;

                default:
                    assert false;
                    return null;
                }
            }

            @Override
            public void postfixWalk_(ResolvedPath pOldParent, OA oa)
                    throws IOException, SQLException
            {
                boolean isRoot = soidRoot.equals(oa.soid());
                boolean oldExpelled = !isRoot && oa.isSelfExpelled();

                if (oldExpelled) return;

                if (oa.isDir()) {
                    ResolvedPath pathOld = isRoot ? pOldParent : pOldParent.join(oa);
                    // have to do it in postfixWalk rather than prefixWalk because otherwise child
                    // objects under the folder would prevent us from deleting the folder.
                    _ps.newFolder_(pathOld).delete_(op, t);
                }

                for (IExpulsionListener l : _expulsion.listeners_()) {
                    l.objectExpelled_(oa.soid(), t);
                }
            }
        });
    }
}
