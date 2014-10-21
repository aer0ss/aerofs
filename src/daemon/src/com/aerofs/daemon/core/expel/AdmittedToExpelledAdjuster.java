package com.aerofs.daemon.core.expel;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.Expulsion.IExpulsionListener;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreDeleter;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;

public class AdmittedToExpelledAdjuster implements IExpulsionAdjuster
{
    private static final Logger l = Loggers.getLogger(AdmittedToExpelledAdjuster.class);

    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final Expulsion _expulsion;
    private final LogicalStagingArea _sa;
    private final StoreDeleter _sd;
    private final IMapSIndex2SID _sidx2sid;
    private final StoreHierarchy _ss;

    @Inject
    public AdmittedToExpelledAdjuster(StoreDeleter sd, Expulsion expulsion,
            IPhysicalStorage ps, LogicalStagingArea sa,
            DirectoryService ds, IMapSIndex2SID sidx2sid, StoreHierarchy ss)
    {
        _sd = sd;
        _expulsion = expulsion;
        _sa = sa;
        _ps = ps;
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
            _ps.deleteFolderRecursively_(pOld, op, t);
        } else  {
            _ps.newFile_(pOld, KIndex.MASTER).delete_(op, t);
        }
        _sa.stageCleanup_(soidRoot, pOld, t);
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
}
