package com.aerofs.daemon.core.store;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.ex.ExNotDir;
import com.aerofs.base.ex.ExNotFound;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.DirectoryService.IObjectWalker;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import org.slf4j.Logger;

public class StoreDeleter
{
    private static final Logger l = Loggers.getLogger(StoreDeleter.class);

    private final IPhysicalStorage _ps;
    private final IStores _ss;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final StoreDeletionOperators _operators;

    @Inject
    public StoreDeleter(IPhysicalStorage ps, DirectoryService ds, IMapSIndex2SID sidx2sid,
            IStores ss, StoreDeletionOperators operators)
    {
        _ss = ss;
        _ps = ps;
        _ds = ds;
        _sidx2sid = sidx2sid;
        _operators = operators;
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
    public void removeParentStoreReference_(SIndex sidx, SIndex sidxParent, Path pathOld,
            PhysicalOp op, Trans t)
            throws SQLException, ExNotFound, ExNotDir, ExStreamInvalid, IOException, ExAlreadyExist
    {
        Set<SIndex> parents = _ss.getParents_(sidx);
        if (parents.size() == 1) {
            assert parents.contains(sidxParent);
            deleteRecursively_(sidx, pathOld, op, t);
        }

        // Remove the parent _after_ deleting the store, since the deletion code may need the
        // parenthood for path resolution, etc.
        _ss.deleteParent_(sidx, sidxParent, t);
    }

    private void deleteRecursively_(SIndex sidx, Path pathOld, PhysicalOp op, Trans t)
            throws SQLException, ExNotFound, ExNotDir, ExStreamInvalid, IOException, ExAlreadyExist
    {
        // delete child stores. go through the store list before actual
        // operations to avoid concurrent modification exceptions
        ArrayList<SIndex> sidxChildren = Lists.newArrayList();
        ArrayList<Path> pathChildren = Lists.newArrayList();

        final Path pathNew = _ds.resolve_(new SOID(sidx, OID.ROOT));

        for (SIndex sidxChild : _ss.getChildren_(sidx)) {
            OID oidAnchor = SID.storeSID2anchorOID(_sidx2sid.get_(sidxChild));
            Path pathNewChild = _ds.resolve_(new SOID(sidx, oidAnchor));
            Path pathOldChild = pathOld;

            for (int i = pathNew.elements().length; i < pathNewChild.elements().length; i++) {
                // creating a new Path object on every iteration is a bit inefficient...
                pathOldChild = pathOldChild.append(pathNewChild.elements()[i]);
            }
            assert pathOldChild.elements().length > pathOld.elements().length;

            sidxChildren.add(sidxChild);
            pathChildren.add(pathOldChild);
        }

        // actual deletion of child stores
        for (int i = 0; i < sidxChildren.size(); i++) {
            removeParentStoreReference_(sidxChildren.get(i), sidx, pathChildren.get(i), op, t);
        }

        // delete physical objects under the store. this must be done
        // *after* children stores are deleted, otherwise we cannot delete
        // physical anchors as they are not empty.
        SOID soidRoot = new SOID(sidx, OID.ROOT);
        for (OID oidChild : _ds.getChildren_(soidRoot)) {
            SOID soidChild = new SOID(sidx, oidChild);
            deletePhysicalObjectsRecursively_(soidChild, pathOld, op, t);
        }

        delete_(sidx, pathOld, op, t);
    }

    private void deletePhysicalObjectsRecursively_(final SOID soidRoot, Path pathOldRoot,
            final PhysicalOp op, final Trans t)
            throws IOException, ExNotFound, SQLException, ExNotDir, ExStreamInvalid, ExAlreadyExist
    {
        _ds.walk_(soidRoot, pathOldRoot, new IObjectWalker<Path>()
        {
            @Override
            public Path prefixWalk_(Path pathOldParent, OA oa)
            {
                l.info("del {} {}", pathOldParent, oa);
                if (oa.type() != Type.DIR) {
                    return null;
                } else if (oa.isExpelled()) {
                    return null;
                } else {
                    return pathOldParent.append(oa.name());
                }
            }

            @Override
            public void postfixWalk_(Path pathOldParent, OA oa)
                    throws IOException, SQLException
            {
                Path path = pathOldParent.append(oa.name());
                l.info("del {}", path);
                switch (oa.type()) {
                case DIR:
                case ANCHOR:
                    if (!oa.isExpelled()) {
                        _ps.newFolder_(oa.soid(), path).delete_(op, t);
                    }
                    break;
                case FILE:
                    // The implementation of prefixWalk_() above avoids walking through expelled
                    // folders by returning null on such folders. And because a file is expelled iff
                    // it's under an expelled folder, we should never walk on an expelled
                    // file.
                    assert !oa.isExpelled();
                    for (KIndex kidx : oa.cas().keySet()) {
                        SOKID sokid = new SOKID(oa.soid(), kidx);
                        _ps.newFile_(sokid, path).delete_(op, t);
                    }
                    break;
                default:
                    assert false;
                }
            }
        });
    }

    /**
     * @param path the path of the store before it's deleted
     */
    private void delete_(final SIndex sidx, Path path, PhysicalOp op, Trans t)
            throws SQLException, IOException, ExStreamInvalid
    {
        l.debug("delete store " + sidx);

        // MJ thinks (but is unsure whether) we have to do physical store deletion first, before
        // runing other deletion operators
        _ps.deleteStore_(sidx, path, op, t);

        _operators.runAll_(sidx, t);
    }
}
