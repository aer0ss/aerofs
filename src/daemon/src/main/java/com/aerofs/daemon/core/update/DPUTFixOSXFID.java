package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil;
import com.google.inject.Inject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static com.aerofs.daemon.core.db.TamperingDetectionSchema.C_DBFILE_FID;
import static com.aerofs.daemon.core.db.TamperingDetectionSchema.T_DBFILE;
import static com.aerofs.daemon.lib.db.CoreSchema.T_OA;
import static com.aerofs.daemon.lib.db.CoreSchema.C_OA_SIDX;
import static com.aerofs.daemon.lib.db.CoreSchema.C_OA_OID;
import static com.aerofs.daemon.lib.db.CoreSchema.C_OA_FID;

/**
 * Dropping support for OS X < 10.7 meant that ino_t went from 32bit to 64bit
 */
public class DPUTFixOSXFID implements IDaemonPostUpdateTask {
    @Inject private IDBCW _dbcw;
    @Inject private IOSUtil _osutil;
    @Inject private InjectableDriver _dr;

    @Override
    public void run() throws Exception {
        if (_osutil.getOSFamily() != OSUtil.OSFamily.OSX) return;

        Loggers.getLogger(DPUTFixOSXFID.class)
                .info("pad FIDs to {} bytes", _dr.getFIDLength());

        DPUTUtil.runDatabaseOperationAtomically_(_dbcw, s -> {
            fixDBFID(s, _dr.getFIDLength());
            fixOAFIDs(s, _dr.getFIDLength());
        });
    }

    private static void fixDBFID(Statement s, int fidLength) throws SQLException {
        byte[] fid = dbFID(s);
        if (fid == null || fid.length >= fidLength) return;

        // pad FID with zeros
        fid = Arrays.copyOf(fid, fidLength);

        try (PreparedStatement ps = s.getConnection().prepareStatement(
                DBUtil.update(T_DBFILE, C_DBFILE_FID))) {
            ps.setBytes(1, fid);
            ps.executeUpdate();
        }
    }

    private static byte[] dbFID(Statement s) throws SQLException {
        try (ResultSet rs = s.executeQuery(DBUtil.select(T_DBFILE, C_DBFILE_FID))) {
            return rs.next() ? rs.getBytes(1) : null;
        }
    }

    private static void fixOAFIDs(Statement s, int fidLength) throws SQLException {
        try (ResultSet rs = s.executeQuery(DBUtil.select(T_OA, C_OA_SIDX, C_OA_OID, C_OA_FID))) {
            while (rs.next()) {
                int sidx = rs.getInt(1);
                byte[] oid = rs.getBytes(2);
                byte[] fid = rs.getBytes(3);
                if (fid == null || fid.length >= fidLength) continue;

                // pad FID with zeros
                fid = Arrays.copyOf(fid, fidLength);

                try (PreparedStatement ps = s.getConnection().prepareStatement(
                        DBUtil.updateWhere(T_OA, C_OA_SIDX + "=? and " + C_OA_OID + "=?", C_OA_FID))) {
                    ps.setBytes(1, fid);
                    ps.setInt(2, sidx);
                    ps.setBytes(3, oid);
                    ps.executeUpdate();
                }
            }
        }
    }
}
