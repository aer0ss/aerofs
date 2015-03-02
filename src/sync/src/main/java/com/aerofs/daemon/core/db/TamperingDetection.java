package com.aerofs.daemon.core.db;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCoreDatabaseParams;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDatabaseParams;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.injectable.InjectableDriver;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import static com.aerofs.daemon.core.db.TamperingDetectionSchema.*;

/**
 * Restoring the core database from backup has the potential to completely wreak the distributed
 * system by corrupting tick space. It can also mess with the linker in some corner cases.
 *
 * This class is in charge of detecting any such tampering. The basic idea being tampering detection
 * is that some metadata about the database file are not expected to change during normal operations
 * but will change when restoring form backup or performing a migration. This include, but may not
 * be limited to:
 *      - FID (i.e. inode number)
 *      - ctime (on file systems that store it) [NB: java 6 does not provide a way to access ctime
 *      so it is not currently used but that will change when we switch to Java 7]
 *
 * NB: This approach depends on SQLite commit logic not replacing the db file with a new file.
 * http://sqlite.org/atomiccommit.html
 * http://sqlite.org/wal.html
 *
 * When the daemon first creates the db it will store this information in the database itself and on
 * every subsequent start it will compare the actual values obtained form the filesystem to those
 * stored in the database.
 *
 * If a mismatch is detected, the daemon exits with {@link ExitCode#CORE_DB_TAMPERING}, which the
 * UI is expected to deal with.
 *
 * The safest behavior in such cases is to reinstall. However there are legitimate cases where we
 * may want to force launch:
 *      - filesystems where FIDs are not persistent across reboot (e.g. FAT32 on Linux)
 *      - full migration from a device to another where the old device is never restarted after the
 *      new one comes online
 *
 * To support these we use a flag file in the rtroot whose presence will cause the tampering check
 * to be bypassed. This flag file is single-use, i.e. it is deleted by the daemon on startup.
 */
public class TamperingDetection
{
    private final static Logger l = Loggers.getLogger(TamperingDetection.class);

    private final IDBCW _dbcw;
    private final IDatabaseParams _params;
    private final InjectableDriver _dr;

    @Inject
    public TamperingDetection(IDBCW dbcw, CfgCoreDatabaseParams params, InjectableDriver dr)
    {
        _dbcw = dbcw;
        _params = params;
        _dr = dr;
    }

    public void init_() throws SQLException, IOException
    {
        FID actual = getActualFID();
        byte[] expected = getExpectedFID();
        File tag = new File(Cfg.absRTRoot(), LibParam.IGNORE_DB_TAMPERING);

        l.info("{} {} {}",
                actual, expected != null ? BaseUtil.hexEncode(expected) : null, tag.exists());

        if (expected == null || Arrays.equals(expected, actual.getBytes()) || tag.exists()) {
            setFID(actual, expected != null);
            tag.delete();
        } else {
            ExitCode.CORE_DB_TAMPERING.exit();
        }
    }

    private byte[] getExpectedFID() throws SQLException
    {
        try (Statement s = _dbcw.getConnection().createStatement()) {
            try (ResultSet rs = s.executeQuery(DBUtil.select(T_DBFILE, C_DBFILE_FID))) {
                return rs.next() ? rs.getBytes(1) : null;
            }
        }
    }

    private FID getActualFID() throws IOException
    {
        return _dr.getFID(getFilePath());
    }

    private void setFID(FID fid, boolean present) throws SQLException
    {
        PreparedStatement ps = _dbcw.getConnection().prepareStatement(present
                ? DBUtil.update(T_DBFILE, C_DBFILE_FID)
                : DBUtil.insert(T_DBFILE, C_DBFILE_FID));
        try {
            ps.setBytes(1, fid.getBytes());
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    private String getFilePath()
    {
        Preconditions.checkState(!_params.isMySQL());
        Preconditions.checkState(_params.url().startsWith("jdbc:sqlite:"));
        return _params.url().substring("jdbc:sqlite:".length());
    }
}
