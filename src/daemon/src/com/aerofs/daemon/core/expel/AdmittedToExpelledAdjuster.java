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
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
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
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

public class AdmittedToExpelledAdjuster implements IExpulsionAdjuster
{
    private static final Logger l = Loggers.getLogger(AdmittedToExpelledAdjuster.class);

    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    private final PrefixVersionControl _pvc;
    private final IPhysicalStorage _ps;
    private final Expulsion _expulsion;
    private final StoreDeleter _sd;
    private final IMapSIndex2SID _sidx2sid;
    private final IStores _ss;

    @Inject
    public AdmittedToExpelledAdjuster(StoreDeleter sd, Expulsion expulsion,
            IPhysicalStorage ps, NativeVersionControl nvc, PrefixVersionControl pvc,
            DirectoryService ds, IMapSIndex2SID sidx2sid, IStores ss)
    {
        _sd = sd;
        _expulsion = expulsion;
        _ps = ps;
        _nvc = nvc;
        _pvc = pvc;
        _ds = ds;
        _ss = ss;
        _sidx2sid = sidx2sid;
    }

    @Override
    public void adjust_(ResolvedPath pOld, final SOID soidRoot, PhysicalOp op, final Trans t)
            throws Exception
    {
        l.info("adm->exp {} {} {}", soidRoot, pOld, op);
        checkArgument(!soidRoot.oid().isRoot());
        OA oa = _ds.getOA_(soidRoot);
        ResolvedPath path = _ds.resolve_(oa);

        // must perform store bookkeeping outside of the subtree walk to preserve
        // externally-observable behavior when the cleanup is done incrementally
        for (SIndex sidx : _ss.getChildren_(soidRoot.sidx())) {
            SID sid = _sidx2sid.get_(sidx);
            SOID anchor = new SOID(soidRoot.sidx(), SID.storeSID2anchorOID(sid));

            // check whether the anchor is in the affected subtree
            ResolvedPath anchorPath = _ds.resolve_(anchor);
            if (!anchorPath.isUnderOrEqual(path)) continue;

            // can't use OA.isExpelled as the expelled flag is already set on soidRoot
            if (isExpelled_(anchorPath, soidRoot)) continue;

            expelAnchor_(sidx, anchor, oldChildPath(pOld, path, anchorPath), op, t);
        }

        // atomic recursive deletion of physical objects, if applicable
        if (oa.isDirOrAnchor()) {
            op = _ps.deleteFolderRecursively_(pOld, op, t);
        }
        cleanup_(soidRoot, pOld, op, t);
    }

    /**
     * Check for presence of an expelled object in a given path, below a given root object
     */
    private boolean isExpelled_(ResolvedPath path, SOID soidRoot) throws SQLException
    {
        int i = path.soids.indexOf(soidRoot);
        checkArgument(i != -1);
        while (++i < path.soids.size()) {
            if (_ds.getOA_(path.soids.get(i)).isSelfExpelled()) return true;
        }
        return false;
    }

    private void expelAnchor_(SIndex sidx, SOID anchor, ResolvedPath pathOld, PhysicalOp op, Trans t)
            throws Exception
    {
        checkArgument(anchor.equals(pathOld.soid()));
        _sd.removeParentStoreReference_(sidx, anchor.sidx(), pathOld, op, t);

        for (IExpulsionListener l : _expulsion.listeners_()) {
            l.anchorExpelled_(anchor, t);
        }
    }

    /**
     * Construct the old path to a child object, given the old and new path to a parent and new
     * path to the child.
     */
    private ResolvedPath oldChildPath(ResolvedPath oldParent, ResolvedPath newParent,
            ResolvedPath newChild)
    {
        return new ResolvedPath(oldParent.sid(),
                ImmutableList.<SOID>builder()
                        .addAll(oldParent.soids)
                        .addAll(newChild.soids
                                .subList(newParent.soids.size(), newChild.soids.size()))
                        .build(),
                ImmutableList.<String>builder()
                        .add(oldParent.elements())
                        .addAll(Arrays.asList(newChild.elements())
                                .subList(newParent.elements().length, newChild.elements().length))
                        .build());
    }

    private void cleanup_(final SOID soidRoot, ResolvedPath pOld, final PhysicalOp op,
            final Trans t)
            throws Exception
    {
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
                    // explicit expulsion is only allowed at folder granularity, and the walk doesn't
                    // enter expelled folders, so we should never walk an expelled file.
                    assert !oldExpelled;

                    SOCID socid = new SOCID(oa.soid(), CID.CONTENT);
                    Version vKMLAdd = Version.empty();
                    for (KIndex kidx : oa.cas().keySet()) {
                        SOCKID k = new SOCKID(socid, kidx);
                        _ps.newFile_(pathOld, kidx).delete_(op, t);
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
                    return null;

                case DIR:
                    // skip children if the expulsion state of the current object doesn't change
                    return oldExpelled ? null : pathOld;

                case ANCHOR:
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

                if (oa.isDirOrAnchor()) {
                    ResolvedPath pathOld = isRoot ? pOldParent : pOldParent.join(oa);
                    // have to do it in postfixWalk rather than prefixWalk because otherwise child
                    // objects under the folder would prevent us from deleting the folder.
                    _ps.newFolder_(pathOld).delete_(op, t);
                }
            }
        });
    }
}
