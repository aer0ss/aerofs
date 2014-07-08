package com.aerofs.daemon.core.store;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.expel.LogicalStagingArea;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.labeling.L;
import com.aerofs.lib.LibParam;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class StoreCreator
{
    private static final Logger l = Loggers.getLogger(StoreCreator.class);

    private IStores _ss;
    private IPhysicalStorage _ps;
    private NativeVersionControl _nvc;
    private ImmigrantVersionControl _ivc;
    private IMetaDatabase _mdb;
    private IMapSID2SIndex _sid2sidx;
    private LogicalStagingArea _sa;

    @Inject
    public void inject_(NativeVersionControl nvc, ImmigrantVersionControl ivc, IMetaDatabase mdb,
            IMapSID2SIndex sid2sidx, IStores ss, IPhysicalStorage ps, LogicalStagingArea sa)
    {
        _ss = ss;
        _nvc = nvc;
        _ivc = ivc;
        _mdb = mdb;
        _sid2sidx = sid2sidx;
        _ps = ps;
        _sa = sa;
    }

    /**
     * Add {@code sidxParent} as {@code sid}'s parent. Create the child store if it doesn't exist.
     */
    public void addParentStoreReference_(SID sid, SIndex sidxParent, String name, Trans t)
            throws Exception
    {
        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) {
            sidx = createStoreImpl_(sid, name, t);
            assert _ss.getParents_(sidx).isEmpty() : sidx + " " + sid;
        } else {
            // adding ref to the root store of a user is never fine
            // adding ref to a regular store that used to have no parent is:
            //   * fine in multiuser because the underlying storage is flat
            //   * problematic in singleuser because it indicates duplication
            assert !sid.isUserRoot() && (L.isMultiuser() || !_ss.getParents_(sidx).isEmpty())
                    : sidx + " " + sid;
        }
        _ss.addParent_(sidx, sidxParent, t);
    }

    /**
     * Create a store with no parent. See comments in IStores for detail
     */
    public SIndex createRootStore_(SID sid, String name, Trans t)
            throws Exception
    {
        return createStoreImpl_(sid, name, t);
    }

    /**
     * Create a store.
     */
    private SIndex createStoreImpl_(SID sid, String name, Trans t)
            throws Exception
    {
        // Note that during store creation, all in-memory data structures may not be fully set up
        // for the new store yet. Therefore, functions involved in store creation might not be able
        // to query information relevant to the store in usual ways. The reliable way to retrieve
        // information is by passing pre-computed values as parameters to the functions when
        // possible. Passing {@code path} to IPhysicalStorage#createStore below is one example.

        SIndex sidx = _sid2sidx.getAbsent_(sid, t);

        // ensure any staged deletion completes
        _sa.ensureStoreClean_(sidx, t);

        l.info("create store {} {}", sidx, sid);

        // create root directory; its parent is itself
        _mdb.insertOA_(sidx, OID.ROOT, OID.ROOT, OA.ROOT_DIR_NAME, OA.Type.DIR, 0, t);

        // create trash directory
        _mdb.insertOA_(sidx, OID.TRASH, OID.ROOT, LibParam.TRASH, OA.Type.DIR, OA.FLAG_EXPELLED_ORG, t);

        _nvc.restoreStore_(sidx, t);
        _ivc.restoreStore_(sidx, t);
        _ps.createStore_(sidx, sid, name, t);
        _ss.add_(sidx, name, t);

        return sidx;
    }

    /**
     * @return true if the remote object specified by {@code oidRemote} is an anchor which was
     * converted from the local object specified by {@code oidLocal}
     */
    public boolean detectFolderToAnchorConversion_(OID oidLocal, OA.Type typeLocal, OID oidRemote,
            OA.Type typeRemote)
    {
        if (!(typeRemote == OA.Type.ANCHOR && typeLocal == OA.Type.DIR)) return false;
        SID sidRemote = SID.anchorOID2storeSID(oidRemote);
        return SID.folderOID2convertedStoreSID(oidLocal).equals(sidRemote) ||
                SID.folderOID2legacyConvertedStoreSID(oidLocal).equals(sidRemote);
    }
}
