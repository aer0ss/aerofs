package com.aerofs.daemon.core.store;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.labeling.L;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

public class StoreDeleter
{
    private static final Logger l = Loggers.getLogger(StoreDeleter.class);

    private final IPhysicalStorage _ps;
    private final StoreHierarchy _ss;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final StoreDeletionOperators _operators;
    private final LocalACL _lacl;
    private final LogicalStagingArea _sa;

    @Inject
    public StoreDeleter(IPhysicalStorage ps, DirectoryService ds, IMapSIndex2SID sidx2sid,
            StoreHierarchy ss, LocalACL lacl, StoreDeletionOperators operators,
            LogicalStagingArea sa)
    {
        _ss = ss;
        _ps = ps;
        _ds = ds;
        _lacl = lacl;
        _sidx2sid = sidx2sid;
        _operators = operators;
        _sa  = sa;
    }

    /**
     * Remove {@code sidxParent} as {@code sidx}'s parent. If the child store has no more parent,
     * delete the store, along with all the store's physical objects and child stores. This method
     * can be called after the caller has updated the location of the store's anchor in the
     * database, but not the location of physical files under the store. If the caller hasn't done
     * that, the parameter {@code pathOld} should point to the current location of the anchor.
     *
     * @param pathOld the old path of the store's anchor before the caller moves it. It's used to
     * locate physical files.
     */
    public void removeParentStoreReference_(SIndex sidx, SIndex sidxParent, ResolvedPath pathOld,
            PhysicalOp op, Trans t)
            throws Exception
    {
        Set<SIndex> parents = _ss.getParents_(sidx);
        if (parents.size() == 1 && canDeleteUnanchored_(sidx)) {
            assert parents.contains(sidxParent);
            deleteRecursively_(sidx, pathOld, op, t);
        }

        // Remove the parent _after_ deleting the store, since the deletion code may need the
        // parenthood for path resolution, etc.
        _ss.deleteParent_(sidx, sidxParent, t);
    }

    /**
     * on Team Server it's acceptable for a store other than a user root to not be anchored
     * in any other store and remain admitted (technically it's also acceptable on a regular
     * client but in that case the ref count never changes).
     *
     * In particular, if an arbitrary folder is shared from a Team Server we MUST NOT delete
     * its content merely because all the members left. Instead we must wait for both
     *   - the ref count to be 0
     *   - ACLs to disappear
     *
     * TODO (WW) use polymorphism to hide multiplicity from this class.
     */
    private boolean canDeleteUnanchored_(SIndex sidx) throws SQLException
    {
        return !L.isMultiuser() || _lacl.get_(sidx).isEmpty();
    }

    public void deleteRootStore_(SIndex sidx, PhysicalOp op, Trans t)
            throws Exception
    {
        assert _ss.isRoot_(sidx);
        deleteRecursively_(sidx, ResolvedPath.root(_sidx2sid.get_(sidx)), op, t);
    }

    private void deleteRecursively_(SIndex sidx, ResolvedPath pathOld, PhysicalOp op, Trans t)
            throws Exception
    {
        // delete child stores. go through the store list before actual
        // operations to avoid concurrent modification exceptions
        ArrayList<SIndex> sidxChildren = Lists.newArrayList();
        ArrayList<ResolvedPath> pathChildren = Lists.newArrayList();

        final ResolvedPath pathNew = _ds.resolve_(new SOID(sidx, OID.ROOT));

        for (SIndex sidxChild : _ss.getChildren_(sidx)) {
            OID oidAnchor = SID.storeSID2anchorOID(_sidx2sid.get_(sidxChild));
            ResolvedPath pathNewChild = _ds.resolve_(new SOID(sidx, oidAnchor));
            ResolvedPath pathOldChild = pathOld;

            for (int i = pathNew.elements().length; i < pathNewChild.elements().length; i++) {
                // creating a new Path object on every iteration is a bit inefficient...
                pathOldChild = pathOldChild.join(pathNewChild.soids.get(i),
                        pathNewChild.elements()[i]);
            }
            assert pathOldChild.elements().length > pathOld.elements().length;

            sidxChildren.add(sidxChild);
            pathChildren.add(pathOldChild);
        }

        // actual deletion of child stores
        for (int i = 0; i < sidxChildren.size(); i++) {
            removeParentStoreReference_(sidxChildren.get(i), sidx, pathChildren.get(i), op, t);
        }

        l.info("delete store {} {}", sidx, pathOld);

        // delete physical objects under the store. this must be done
        // *after* children stores are deleted, otherwise we cannot delete
        // physical anchors as they are not empty.

        // atomic recursive deletion of physical objects, if applicable
        _ps.deleteFolderRecursively_(pathOld, op, t);

        // Run immediate deletion operators now; then call into the staging area, which will
        // finalize and run the deferred operators.
        _operators.runAllImmediate_(sidx, t);
        _sa.stageCleanup_(new SOID(sidx, OID.ROOT), pathOld, t);
    }
}
