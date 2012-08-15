package com.aerofs.daemon.core.object;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.expel.Expulsion;
import com.aerofs.daemon.core.migration.ImmigrantDetector;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.core.store.StoreCreator;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.*;
import com.google.inject.Inject;

import static com.aerofs.daemon.core.ds.OA.*;

public class ObjectCreator
{
    private DirectoryService _ds;
    private ComMonitor _cm;
    private ImmigrantDetector _imd;
    private Expulsion _ex;
    private StoreCreator _sc;

    @Inject
    public void inject_(DirectoryService ds, ComMonitor cm, ImmigrantDetector imd, Expulsion ex,
            StoreCreator sc)
    {
        _ds = ds;
        _cm = cm;
        _imd = imd;
        _ex = ex;
        _sc = sc;
    }

    /**
     * Create a new object. Create an empty physical file if it's a file and it's not
     * linker-initiated.
     */
    public void create_(Type type, SOID soidParent, String name, PhysicalOp op, Trans t)
            throws Exception
    {
        SOID soid = new SOID(soidParent.sidx(), new OID(UniqueID.generate()));
        createMeta_(type, soid, soidParent.oid(), name, 0, op, false, true, t);

        if (type == OA.Type.FILE) {
            final KIndex kidx = KIndex.MASTER;

            _ds.createCA_(soid, kidx, t);

            _ds.getOANullable_(soid).caMaster().physicalFile().create_(op, t);

            _cm.atomicWrite_(new SOCKID(soid, CID.CONTENT, kidx), t);
        }
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
        // the caller mustn't set these flags
        assert !Util.test(flags, FLAG_EXPELLED_ORG_OR_INH);

        // determine expelled flags
        OA oaParent = _ds.getOAThrows_(new SOID(soid.sidx(), oidParent));
        boolean expelled;
        if (oaParent.isExpelled()) {
            // we can't map an expelled object since an expelled logical object doesn't have a
            // corresponding physical object.
            assert op != PhysicalOp.MAP;
            flags |= FLAG_EXPELLED_INH;
            expelled = true;
        } else {
            expelled = false;
        }

        _ds.createOA_(type, soid.sidx(), soid.oid(), oidParent, name, flags, t);

        if (updateVersion) _cm.atomicWrite_(new SOCKID(soid, CID.META, KIndex.MASTER), t);

        boolean immigrated = !detectImmigration || expelled ? false :
            _imd.detectAndPerformImmigration_(_ds.getOA_(soid), op, t);

        OA oa = _ds.getOA_(soid);
        if (!expelled && !immigrated) {
            // create physical object and sub-store
            if (oa.isDir()) {
                oa.physicalFolder().create_(op, t);
            } else if (oa.isAnchor()) {
                oa.physicalFolder().create_(op, t);
                _sc.createStore_(SID.anchorOID2storeSID(soid.oid()), soid.sidx(), _ds.resolve_(oa),
                        t);
            } else {
                assert oa.isFile();
            }
        }

        if (expelled && oa.isFile()) _ex.fileExpelled_(soid);
    }
}
