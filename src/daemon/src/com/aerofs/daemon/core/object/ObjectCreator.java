package com.aerofs.daemon.core.object;

import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UniqueID;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
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
    private final ImmigrantDetector _imd;
    private final StoreCreator _sc;

    @Inject
    public ObjectCreator(DirectoryService ds, VersionUpdater vu, ImmigrantDetector imd,
            StoreCreator sc, IPhysicalStorage ps)
    {
        _ds = ds;
        _vu = vu;
        _imd = imd;
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
        return create_(type, new OID(UniqueID.generate()), soidParent, name, op, t);
    }

    /**
     * Create a new object. Create an empty physical file if it's a file and it's not
     * linker-initiated.
     */
    public SOID create_(Type type, OID oid, SOID soidParent, String name, PhysicalOp op, Trans t)
            throws Exception
    {
        SOID soid = new SOID(soidParent.sidx(), oid);
        createMeta_(type, soid, soidParent.oid(), name, 0, op, false, true, t);

        if (type == OA.Type.FILE) {
            final KIndex kidx = KIndex.MASTER;

            _ds.createCA_(soid, kidx, t);

            IPhysicalFile pf = _ps.newFile_(_ds.resolve_(soid), kidx);
            pf.create_(op, t);

            _ds.setCA_(new SOKID(soid, kidx), pf.getLength_(),
                    pf.getLastModificationOrCurrentTime_(), null, t);

            _vu.update_(new SOCKID(soid, CID.CONTENT, kidx), t);
        }
        return soid;
    }

    /**
     * create the metadata of a new object with the specified OID, and perform
     * all the logistics required by the new metadata including creation or
     * immigration of physical folders and anchored stores
     */
    public void createMeta_(OA.Type type, final SOID soid, OID oidParent, String name, int flags,
            PhysicalOp op, boolean detectImmigration, boolean updateVersion, Trans t)
            throws Exception
    {
        boolean expelled = createOA_(type, soid, oidParent, name, flags, op, updateVersion, t);

        boolean immigrated = !detectImmigration || expelled ? false :
            _imd.detectAndPerformImmigration_(_ds.getOA_(soid), op, t);

        adjustPhysicalObject_(soid, expelled, immigrated, op, t);
    }

    /**
     * Create the metadata for an object being migrated from a different store
     */
    public void createImmigrantMeta_(OA.Type type, SOID soidFrom, SOID soidTo, OID oidToParent,
            String name, PhysicalOp op, boolean updateVersion, Trans t)
            throws ExNotFound, ExAlreadyExist, SQLException, IOException
    {
        boolean expelled = createOA_(type, soidTo, oidToParent, name, 0, op, updateVersion, t);

        boolean immigrated = !expelled && type == Type.FILE;
        if (immigrated) _imd.immigrateFile_(_ds.getOA_(soidFrom), _ds.getOA_(soidTo), op, t);

        adjustPhysicalObject_(soidTo, expelled, immigrated, op, t);
    }

    private boolean createOA_(OA.Type type, final SOID soid, OID oidParent, String name, int flags,
            PhysicalOp op,  boolean updateVersion, Trans t)
            throws ExNotFound, ExAlreadyExist, SQLException, IOException
    {
        // determine expelled flags
        OA oaParent = _ds.getOAThrows_(new SOID(soid.sidx(), oidParent));
        boolean expelled;
        if (oaParent.isExpelled()) {
            // we can't map an expelled object since an expelled logical object doesn't have a
            // corresponding physical object.
            assert op != PhysicalOp.MAP;
            expelled = true;
        } else {
            expelled = false;
        }

        _ds.createOA_(type, soid.sidx(), soid.oid(), oidParent, name, flags, t);

        if (updateVersion) _vu.update_(new SOCKID(soid, CID.META, KIndex.MASTER), t);

        return expelled;
    }

    private void adjustPhysicalObject_(SOID soid, boolean expelled, boolean immigrated,
            PhysicalOp op, Trans t)
            throws ExNotFound, SQLException, ExAlreadyExist, IOException
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
