/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DPUTAddAggregateSyncColumn implements IDaemonPostUpdateTask
{
    private final static Logger l = Util.l(DPUTAddAggregateSyncColumn.class);

    private final IDBCW _dbcw;
    private final TransManager _tm;
    private final IStoreDatabase _sdb;
    private final IMetaDatabase _mdb;
    private final MapSIndex2DeviceBitMap _sidx2dbm;

    DPUTAddAggregateSyncColumn(CoreDBCW dbcw, TransManager tm, IStoreDatabase sdb, IMetaDatabase mdb,
            MapSIndex2DeviceBitMap sidx2dbm)
    {
        _dbcw = dbcw.get();
        _tm = tm;
        _sdb = sdb;
        _mdb = mdb;
        _sidx2dbm = sidx2dbm;
    }

    /**
     * @pre {@code soid} exists and is a directory
     */
    private int getSyncableChildCount_(SOID soid) throws SQLException
    {
        int total = 0;
        for (OID oid : _mdb.getChildren_(soid)) {
            OA coa = _mdb.getOA_(new SOID(soid.sidx(), oid));
            if (!coa.isExpelled()) {
                ++total;
            }
        }
        return total;
    }

    /**
     * Set the aggregate sync status for an object
     */
    private void setAggregateSyncStatus_(SOID soid, CounterVector cv) throws SQLException
    {
        Trans t = _tm.begin_();
        try {
            _mdb.setAggregateSyncStatus_(soid, cv, t);
            t.commit_();
        } finally {
            t.end_();
        }
    }

    /**
     * Recursively aggregate sync status within store boundaries
     */
    private BitVector aggregateRecursivelyWithinStore_(SOID soid) throws SQLException
    {
        OA oa = _mdb.getOA_(soid);
        if (oa.isDir()) {
            CounterVector cv = _mdb.getAggregateSyncStatus_(soid);

            // if a previous run of the post-update task was interrupted (e.g possibly killed by the
            // user because the startup was so long that it looked like the daemon was hanging...)
            // re-use whatever was successfully computed to ensure some progress is made
            if (cv.size() == 0) {
                // no aggregate yet, recursively populate the column
                SIndex sidx = soid.sidx();
                for (OID coid : _mdb.getChildren_(soid)) {
                    OA coa = _mdb.getOA_(new SOID(soid.sidx(), coid));
                    if (!coa.isExpelled()) {
                        BitVector cbv = aggregateRecursivelyWithinStore_(new SOID(sidx, coid));
                        for (int i = cbv.findFirstSetBit(); i != -1; i = cbv.findNextSetBit(i + 1)) {
                            cv.inc(i);
                        }
                    }
                }
                setAggregateSyncStatus_(soid, cv);
            }

            // derive aggregate status bit vector from counter vector
            int deviceCount = _sidx2dbm.getDeviceMapping_(soid.sidx()).size();
            BitVector bv = cv.elementsEqual(getSyncableChildCount_(soid), deviceCount);
            if (!soid.oid().isRoot()) {
                // for non-root directories, take sync status of directory itself into account
                bv.andInPlace(_mdb.getSyncStatus_(soid));
            }

            l.debug(soid + " => " + cv + " " + bv);
            return bv;
        } else {
            // files and anchors are treated similarly as this method is called for each store root
            // and the aggregate sync status column only has store-local data
            return _mdb.getSyncStatus_(soid);
        }
    }

    @Override
    public void run() throws Exception
    {
        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();
        Statement s = c.createStatement();
        try {
            if (!_dbcw.columnExists(T_OA, C_OA_AG_SYNC)) {
                s.executeUpdate("alter table " + T_OA + " add column " + C_OA_AG_SYNC + " blob");
            }
        } finally {
            s.close();
        }
        c.commit();

        // populate the aggregate sync status column by recursively aggregating sync status for each
        // store root
        for (SIndex sidx : _sdb.getAll_()) {
            l.warn("-> " + sidx);
            SOID croot = new SOID(sidx, OID.ROOT);
            aggregateRecursivelyWithinStore_(croot);
        }
    }
}
