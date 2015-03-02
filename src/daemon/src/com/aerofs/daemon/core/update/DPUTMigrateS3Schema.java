/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.daemon.core.phy.block.BlockStorageSchema;
import com.aerofs.daemon.core.phy.block.cache.CacheSchema;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.cfg.CfgDatabase;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.common.collect.Maps;

import static com.aerofs.daemon.core.phy.block.BlockStorageSchema.*;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map.Entry;
import java.util.SortedMap;

/**
 * Migrate the old S3 schema to the new BlockStorage schema
 *
 * Mostly changing table names and dropping redundant columns but some complexity arises
 * due to the use of autoincrement primary keys.
 *
 * For the sake of simplicity, cached blocks are simply discarded instead of being migrated to the
 * new cache location
 */
public class DPUTMigrateS3Schema implements IDaemonPostUpdateTask
{
    private static final String
            T_S3_FileInfo              = "s3fi",
            C_S3_FileInfo_Index        = "s3fi_idx",
            C_S3_FileInfo_InternalName = "s3fi_id";

    private static final String
            T_S3_FileCurr              = "s3fc",
            C_S3_FileCurr_Index        = "s3fc_idx",
            C_S3_FileCurr_Ver          = "s3fc_ver",
            C_S3_FileCurr_Len          = "s3fc_len",
            C_S3_FileCurr_Date         = "s3fc_date",
            C_S3_FileCurr_Chunks       = "s3fc_chunks";

    private static final String
            T_S3_FileHist              = "s3fh",
            C_S3_FileHist_Index        = "s3fh_idx",
            C_S3_FileHist_Ver          = "s3fh_ver",
            C_S3_FileHist_Parent       = "s3fh_parent",  // C_DirHist_Index
            C_S3_FileHist_RealName     = "s3fh_name",
            C_S3_FileHist_Len          = "s3fh_len",
            C_S3_FileHist_Date         = "s3fh_date",
            C_S3_FileHist_Chunks       = "s3fh_chunks";

    private static final String
            T_S3_DirCurr               = "s3dc";

    private static final String
            T_S3_DirHist               = "s3dh",
            C_S3_DirHist_Index         = "s3dh_idx",
            C_S3_DirHist_Parent        = "s3dh_parent",
            C_S3_DirHist_Name          = "s3dh_name";

    private static final String
            T_S3_ChunkCount            = "s3co",
            C_S3_ChunkCount_Hash       = "s3co_hash",
            C_S3_ChunkCount_Len        = "s3co_len",
            C_S3_ChunkCount_State      = "s3co_state",
            C_S3_ChunkCount_Count      = "s3co_count";

    private static final String
            T_S3_ChunkCache            = "s3ca";

    private final IDBCW _dbcw;
    private final CfgDatabase _cfgdb;

    public DPUTMigrateS3Schema(IDBCW dbcw, CfgDatabase cfgdb)
    {
        _dbcw = dbcw;
        _cfgdb = cfgdb;
    }

    @Override
    public void run() throws Exception
    {
        // this migration is only required for S3 clients
        if (!_dbcw.tableExists(T_S3_FileInfo)) return;

        Connection c = _dbcw.getConnection();
        assert !c.getAutoCommit();

        try (Statement s = c.createStatement()) {
            // create new tables
            new BlockStorageSchema().create_(s, _dbcw);
            new CacheSchema().create_(s, _dbcw);

            // migrate data (NB: order matters)
            migrateFileIndices(s);
            migrateFileCurrent(s);
            migrateFileHist(s);
            migrateDirHist(c, s);
            migrateBlockCount(s);

            // delete old tables
            s.executeUpdate("drop table " + T_S3_FileInfo);
            s.executeUpdate("drop table " + T_S3_FileCurr);
            s.executeUpdate("drop table " + T_S3_FileHist);
            s.executeUpdate("drop table " + T_S3_DirCurr);
            s.executeUpdate("drop table " + T_S3_DirHist);
            s.executeUpdate("drop table " + T_S3_ChunkCount);
            s.executeUpdate("drop table " + T_S3_ChunkCache);

            // delete obsolete dir structure
            FileUtil.deleteIgnoreErrorRecursively(new File(_cfgdb.get(Key.S3_DIR)));
        }

        c.commit();
    }

    /**
     * NB: the indices may differ between the old and new tables
     * therefore other migration functions dealing with indices will need to be careful...
     */
    private void migrateFileIndices(Statement s) throws SQLException
    {
        s.executeUpdate("insert into " + T_FileInfo + "(" + C_FileInfo_InternalName + ")" +
                " select " + C_S3_FileInfo_InternalName + " from " + T_S3_FileInfo);
    }

    /**
     * Killer double-join to replace the old file index with the new one in case
     * the old table had gaps (SQLite autoincrement guarantees increasing values but
     * not absence of gaps in the sequence)
     *
     * First join to obtain the internal name for the (old) file index
     * Second join to get the new index for the internal name
     *
     * This only works because we have a 1:1 mapping of indices and internal names
     */
    private void migrateFileCurrent(Statement s) throws SQLException
    {
        s.executeUpdate("insert into " + T_FileCurr + "(" +
                C_FileCurr_Index + "," +
                C_FileCurr_Ver + "," +
                C_FileCurr_Len + "," +
                C_FileCurr_Date + "," +
                C_FileCurr_Chunks + ")" +
                " select " +
                C_FileInfo_Index + "," +
                C_S3_FileCurr_Ver + "," +
                C_S3_FileCurr_Len + "," +
                C_S3_FileCurr_Date + "," +
                C_S3_FileCurr_Chunks +
                " from " + T_S3_FileCurr +
                " inner join " + T_S3_FileInfo +
                " on " + C_S3_FileCurr_Index + "=" + C_S3_FileInfo_Index +
                " inner join " + T_FileInfo +
                " on " + C_S3_FileInfo_InternalName + "=" + C_FileInfo_InternalName);
    }

    /**
     * Killer double join again to replace old file index with new one
     *
     * An extra step is needed to fix parent indices
     */
    private void migrateFileHist(Statement s) throws SQLException
    {
        s.executeUpdate("insert into " + T_FileHist + "(" +
                C_FileHist_Index + "," +
                C_FileHist_Ver + "," +
                C_FileHist_Parent + "," +
                C_FileHist_RealName + "," +
                C_FileHist_Len + "," +
                C_FileHist_Date + "," +
                C_FileHist_Chunks + ")" +
                " select " +
                C_FileInfo_Index + "," +
                C_S3_FileHist_Ver + "," +
                C_S3_FileHist_Parent + "," +  // may need fixin'
                C_S3_FileHist_RealName + "," +
                C_S3_FileHist_Len + "," +
                C_S3_FileHist_Date + "," +
                C_S3_FileHist_Chunks +
                " from " + T_S3_FileHist +
                " inner join " + T_S3_FileInfo +
                " on " + C_S3_FileHist_Index + "=" + C_S3_FileInfo_Index +
                " inner join " + T_FileInfo +
                " on " + C_S3_FileInfo_InternalName + "=" + C_FileInfo_InternalName);
    }

    /**
     * We can't do a dumb migration due to the use of autoincrement keys and we can't do
     * anything smart with SQL as the parent column refers to other rows on the same table.
     *
     * Simply iterate over history directory, by ascending index and recreate equivalent
     * directories, offseting the parent index as needed if the sequence happens to change
     *
     * Additionaly, we need to fix the parent column of the file history table in case of
     * gap-induced changes in the index sequence.
     */
    private void migrateDirHist(Connection c, Statement s) throws SQLException
    {
        ResultSet rs = s.executeQuery("select " +
                C_S3_DirHist_Index + "," + C_S3_DirHist_Parent + "," + C_S3_DirHist_Name +
                " from " + T_S3_DirHist);

        // keep track of maximum new dir index
        long max = -1;
        // keep track of any index that differ due to gaps in the migrated table
        SortedMap<Long, Long> diff = Maps.newTreeMap();
        try {
            while (rs.next()) {
                long oldIdx = rs.getLong(1);
                long oldParent = rs.getLong(2);
                Long newParent = diff.get(oldParent);
                long parent = newParent != null ? newParent : oldParent;

                // make sure we're not referencing a directory that does not exist
                assert parent <= max;

                PreparedStatement ps = c.prepareStatement("insert into " + T_DirHist + "(" +
                        C_DirHist_Parent + "," + C_DirHist_Name + ") values(?,?)",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                ps.setLong(1, parent);
                ps.setString(2, rs.getString(3));
                ps.executeUpdate();

                // retrieve generated index
                long newIdx = DBUtil.generatedId(ps);
                if (newIdx != oldIdx) diff.put(oldIdx, newIdx);
                max = newIdx;
            }
        } finally {
            rs.close();
        }

        /*
         * now fix up file history if needed
         *
         * NOTE: although the old values may have arbitrary gaps we do not expect the new values to
         * have any gaps since we will not reach this point if any of the insertion fails (SQLite
         * docs imply that barring failed operations, the increment should be one). We actually need
         * a weaker guarantee that that: as long as the new id is less than the old one the updates
         * will not conflict thanks to the use of a SortedMap.
         */
        for (Entry<Long, Long> e : diff.entrySet()) {
            // make sure we do not risk running into conflicts
            assert e.getKey() > e.getValue();
            s.executeUpdate("update " + T_FileHist +
                    " set "   + C_FileHist_Parent + "=" + String.valueOf(e.getValue()) +
                    " where " + C_FileHist_Parent + "=" + String.valueOf(e.getKey()));
        }
    }

    /**
     * Nothing fancy here, just dumb migration
     */
    private void migrateBlockCount(Statement s) throws SQLException
    {
        s.executeUpdate("insert into " + T_BlockCount + "(" +
                C_BlockCount_Hash + "," +
                C_BlockCount_Len + "," +
                C_BlockCount_State + "," +
                C_BlockCount_Count + ")" +
                " select " +
                C_S3_ChunkCount_Hash + "," +
                C_S3_ChunkCount_Len + "," +
                C_S3_ChunkCount_State + "," +
                C_S3_ChunkCount_Count +
                " from " + T_S3_ChunkCount);
    }
}
