/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.phy.linked.db.NRODatabase;
import com.aerofs.daemon.lib.db.IMetaDatabase;
import com.aerofs.daemon.lib.db.ISIDDatabase;
import com.aerofs.daemon.lib.db.IStoreDatabase;
import com.aerofs.daemon.lib.db.MetaDatabase;
import com.aerofs.daemon.lib.db.SIDDatabase;
import com.aerofs.daemon.lib.db.StoreDatabase;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema.*;

/**
 * Fixes SUPPORT-1239 (and related)
 *
 * More fallout from the NFC/NFD mess on OSX
 *
 * {@link com.aerofs.daemon.core.update.DPUTFixNormalizationOSX} addressed the tip of the iceberg.
 * However children of NFC folders that were synced during the (fairly large) window between the
 * 0.4.214 and 0.4.265 pushes ended up as CNRO because their parent was in the filesystem instead
 * of being in the nro aux folder and the write failed...
 *
 * Manually renaming each affected file to the same name via the "Unsyncable Files" dialog is not
 * the kind of UX we want our user to go through so this DPUT attempt to move every CNRO (except
 * for those that conflict with another object) to its preferred physical location.
 */
public class DPUTFixCNROsOnOSX implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTFixCNROsOnOSX.class);

    private final IDBCW _dbcw;
    private final IOSUtil _osutil;
    private final IMetaDatabase _mdb;
    private final IStoreDatabase _sdb;
    private final ISIDDatabase _siddb;
    private final NRODatabase _nrodb;

    public DPUTFixCNROsOnOSX(IOSUtil osutil, IDBCW dbcw)
    {
        _dbcw = dbcw;
        _osutil = osutil;
        _mdb = new MetaDatabase(dbcw);
        _sdb = new StoreDatabase(dbcw);
        _siddb = new SIDDatabase(dbcw);
        _nrodb = new NRODatabase(dbcw);
    }

    @Override
    public void run() throws Exception
    {
        if (Cfg.storageType() != StorageType.LINKED || !OSUtil.isOSX()) return;

        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, this::fixCNROs);
    }

    private void fixCNROs(Statement s) throws SQLException
    {
        ResultSet rs = s.executeQuery(DBUtil.selectWhere(T_NRO,
                C_NRO_CONFLICT_OID + " is null",
                C_NRO_SIDX, C_NRO_OID));

        // NB: we cannot remove entries while we're iterating the ResultSet hence this list
        // This opens up the possibility of running into an OOM for large numbers of CNROs
        // to be fixed, however the likelihood of someone actually running into that issue
        // is low enough that we won't bother making this DPUT more robust unless it turns
        // out to be necessary.
        List<SOID> del = Lists.newArrayList();
        try {
            while (rs.next()) {
                SOID soid = new SOID(new SIndex(rs.getInt(1)), new OID(rs.getBytes(2)));
                if (fix(soid)) del.add(soid);
            }
        } finally {
            rs.close();
        }

        cleanup(s, del);
    }

    private void cleanup(Statement s, Iterable<SOID> del) throws SQLException
    {
        PreparedStatement ps = s.getConnection().prepareStatement(DBUtil.deleteWhere(T_NRO,
                C_NRO_SIDX + "=? and " + C_NRO_OID + "=?"));

        for (SOID soid : del) {
            ps.setInt(1, soid.sidx().getInt());
            ps.setBytes(2, soid.oid().getBytes());
            ps.executeUpdate();
        }
    }

    /**
     * @return whether the entry for that object in the NRO table should be removed
     */
    private boolean fix(SOID soid) throws SQLException
    {
        OA oa = _mdb.getOA_(soid);

        // check for old expulsion flags
        if (oa == null || Util.test(oa.flags(), 3)) {
            l.warn("cnro for non-existing obj {} {}", soid, oa);
            return true;
        }

        ResolvedPath path = DPUTFixNormalizationOSX.resolve(_sdb, _siddb, _mdb, oa);
        if (path.isInTrash()) {
            l.warn("cnro for trashed object {} {}", soid, path);
            return true;
        }

        String nroPath = DPUTFixNormalizationOSX.nroPath(path);
        String phyPath = Util.join(physicalPath(path.parent()), path.last());

        File nro = new File(nroPath);
        File phy = new File(phyPath);

        l.info("attempt fix {} {} {}", soid, nro, phy);

        if (!nro.exists()) {
            l.info("source missing (incremental progress)");
            return true;
        }

        if (phy.exists()) {
            l.info("target already exists");
            return false;
        }

        try {
            FileUtil.moveInSameFileSystem(nro, phy);
        } catch (IOException e) {
            l.info("could not fix cnro", e);
            return false;
        }

        return true;
    }

    private String physicalPath(ResolvedPath path) throws SQLException
    {
        return DPUTFixNormalizationOSX.physicalPath(_osutil, _nrodb, path);
    }
}
