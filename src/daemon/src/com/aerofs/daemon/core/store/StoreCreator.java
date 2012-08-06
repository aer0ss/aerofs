package com.aerofs.daemon.core.store;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.ImmigrantVersionControl;
import com.aerofs.daemon.core.phy.IPhysicalStorage;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.C;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgRootSID;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

import java.io.IOException;
import java.sql.SQLException;

import javax.annotation.Nullable;

public class StoreCreator
{
    private IStores _ss;
    private IPhysicalStorage _ps;
    private NativeVersionControl _nvc;
    private ImmigrantVersionControl _ivc;
    private IMetaDatabase _mdb;
    private CfgRootSID _cfgRootSID;
    private IMapSID2SIndex _sid2sidx;

    @Inject
    public void inject_(CfgRootSID cfgRootSID, IMapSIndex2Store sidx2s, NativeVersionControl nvc,
            ImmigrantVersionControl ivc, IMetaDatabase mdb, IMapSID2SIndex sid2sidx, IStores ss,
            IPhysicalStorage ps)
    {
        _cfgRootSID = cfgRootSID;
        _ss = ss;
        _nvc = nvc;
        _ivc = ivc;
        _mdb = mdb;
        _sid2sidx = sid2sidx;
        _ps = ps;
    }

    /**
     * Create a new store.
     */
    public void createStore_(SID sid, SIndex sidxParent, Path path, Trans t)
            throws ExAlreadyExist, SQLException, IOException
    {
        createStoreImpl_(sid, sidxParent, path, t);
    }

    /**
     * Create the root store.
     */
    public SIndex createRootStore_(Trans t) throws ExAlreadyExist, SQLException, IOException
    {
        return createStoreImpl_(_cfgRootSID.get(), null, new Path(), t);
    }

    /**
     * @param sidxParent the parent store's sidx, null if the store being created is the root.
     */
    private SIndex createStoreImpl_(SID sid, @Nullable SIndex sidxParent, Path path, Trans t)
            throws SQLException, ExAlreadyExist, IOException
    {
        // Note that during store creation, all in-memory data structures may not be fully set up
        // for the new store yet. Therefore, functions involved in store creation might not be able
        // to query information relevant to the store in usual ways. The reliable way to retrieve
        // information is by passing pre-computed values as parameters to the functions when
        // possible. Passing {@code path} to IPhysicalStorage#createStore below is one example.

        SIndex sidx = _sid2sidx.getAbsent_(sid, t);

        Util.l(this).info("create store " + sidx);

        // create root directory; its parent is itself
        _mdb.createOA_(sidx, OID.ROOT, OID.ROOT, OA.ROOT_DIR_NAME, OA.Type.DIR, 0, t);

        // create trash directory
        _mdb.createOA_(sidx, OID.TRASH, OID.ROOT, C.TRASH, OA.Type.DIR, OA.FLAG_EXPELLED_ORG, t);

        _nvc.restoreStore_(sidx, t);
        _ivc.restoreStore_(sidx, t);
        _ps.createStore_(sidx, path, t);
        _ss.add_(sidx, sidxParent == null ? sidx : sidxParent, t);

        return sidx;
    }

    /**
     * @return true if the remote object specified by {@code oidRemote} is an anchor which was
     * converted from the local object specified by {@code oidLocal}
     */
    public boolean detectFolderToAnchorConversion_(OID oidLocal, OA.Type typeLocal, OID oidRemote,
            OA.Type typeRemote)
    {
        return typeRemote == OA.Type.ANCHOR && typeLocal == OA.Type.DIR &&
            SID.storeSID2anchorOID(SID.folderOID2convertedStoreSID(oidLocal)).equals(oidRemote);
    }
}
