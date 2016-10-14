package com.aerofs.daemon.core.object;

import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.phy.IPhysicalFile;
import com.aerofs.daemon.core.phy.IPhysicalFolder;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNotFound;
import com.aerofs.lib.id.*;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

import static com.aerofs.daemon.core.ds.OA.*;

public class ObjectCreator
{
    private final DirectoryService _ds;
    private final IPhysicalStorage _ps;
    private final VersionUpdater _vu;
    private final StoreCreator _sc;

    @Inject
    public ObjectCreator(DirectoryService ds, VersionUpdater vu,
            StoreCreator sc, IPhysicalStorage ps)
    {
        _ds = ds;
        _vu = vu;
        _sc = sc;
        _ps = ps;
    }

    /**
     * Create a new object. Create an empty physical file if it's a file and it's not
     * linker-initiated.
     */
    public SOID create_(Type type, SOID soidParent, String name, PhysicalOp op, Trans t)
            throws Exception
    {
        SOID soid = createMeta_(type, soidParent, name, op, t);
        if (type == Type.FILE) {
            final KIndex kidx = KIndex.MASTER;

            _ds.createCA_(soid, kidx, t);

            IPhysicalFile pf = _ps.newFile_(_ds.resolve_(soid), kidx);
            pf.create_(op, t);

            _ds.setCA_(new SOKID(soid, kidx), pf.lengthOrZeroIfNotFile(),
                    pf.lastModified(), null, t);

            _vu.update_(new SOCID(soid, CID.CONTENT), t);
        }
        return soid;
    }

    /**
     * Create metadata for a new object.
     *
     * May create an empty folder but will NOT create empty files
     */
    public SOID createMeta_(Type type, SOID soidParent, String name, PhysicalOp op, Trans t)
            throws Exception
    {
        SOID soid = new SOID(soidParent.sidx(), OID.generate());
        createMeta_(type, soid, soidParent.oid(), name, op, true, t);
        return soid;
    }

    /**
     * Create metadata for a new object detected by the linker/scanner.
     */
    public SOID createMetaForLinker_(Type type, OID oid, SOID soidParent, String name, Trans t)
            throws Exception
    {
        SOID soid = new SOID(soidParent.sidx(), oid);
        createMeta_(type, soid, soidParent.oid(), name, PhysicalOp.MAP, true, t);
        if (type == Type.FILE) {
            _ps.newFile_(_ds.resolve_(soid), KIndex.MASTER).create_(PhysicalOp.MAP, t);
        }
        return soid;
    }

    /**
     * create the metadata of a new object with the specified OID, and perform
     * all the logistics required by the new metadata including creation or
     * immigration of physical folders and anchored stores
     */
    public void createMeta_(Type type, final SOID soid, OID oidParent, String name, PhysicalOp op,
                            boolean updateVersion, Trans t)
            throws Exception
    {
        boolean expelled = createOA_(type, soid, oidParent, name, updateVersion, t);

        adjustPhysicalObject_(soid, expelled, false, op, t);
    }

    public boolean createOA_(Type type, final SOID soid, OID oidParent, String name,
                             boolean updateVersion, Trans t)
            throws ExNotFound, ExAlreadyExist, SQLException, IOException
    {
        // determine expelled flags
        OA oaParent = _ds.getOAThrows_(new SOID(soid.sidx(), oidParent));
        boolean expelled;
        expelled = oaParent.isExpelled();

        _ds.createOA_(type, soid.sidx(), soid.oid(), oidParent, name, t);

        if (updateVersion) _vu.update_(new SOCID(soid, CID.META), t);

        return expelled;
    }

    private void adjustPhysicalObject_(SOID soid, boolean expelled, boolean immigrated,
            PhysicalOp op, Trans t)
            throws Exception
    {

        OA oa = _ds.getOA_(soid);
        if (!expelled && !immigrated) {
            // create physical object and sub-store
            if (oa.isDir()) {
                _ps.newFolder_(_ds.resolve_(oa)).create_(op, t);
            } else if (oa.isAnchor()) {
                SID sid =SID.anchorOID2storeSID(oa.soid().oid());
                IPhysicalFolder pf = _ps.newFolder_(_ds.resolve_(oa));
                pf.create_(op, t);
                _sc.addParentStoreReference_(sid, oa.soid().sidx(), oa.name(), t);
                pf.promoteToAnchor_(sid, op, t);
            } else {
                assert oa.isFile();
            }
        }
    }
}
