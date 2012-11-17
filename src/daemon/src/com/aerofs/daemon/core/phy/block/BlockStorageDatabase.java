/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.ex.ExFormatError;
import com.google.inject.Inject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.aerofs.daemon.core.phy.block.BlockStorageSchema.*;
import static com.aerofs.daemon.core.phy.block.BlockUtil.isOneBlock;

/**
 * Maintains mapping between logical object (SOKID) and physical objects (64bit index)
 *
 * For each physical object, keep track of:
 *      version (64bit integer), to distinguish revisions
 *      length
 *      mtime
 *      content (ordered list of blocks)
 *
 * For each block, keep track of:
 *      length
 *      content hash
 *      reference count
 *      state (to handle remote storage backends)
 *
 * In addition to the "live" physical objects, this databse also tracks "dead" ones to provide
 * revision history. Things get a little more involved as the IPhysicalRevProvider interface
 * uses Path instead of logical objects. We therefore have to maintain an alternate object tree
 * to track revisions.
 */
public class BlockStorageDatabase extends AbstractDatabase
{
    private static final Logger l = Util.l(BlockStorageDatabase.class);

    public static final long FILE_ID_NOT_FOUND = -1;

    public static final long DIR_ID_NOT_FOUND = -1;
    public static final long DIR_ID_ROOT = -2;

    public static final long DELETED_FILE_LEN = -1; //C.S3_DELETED_FILE_LEN;
    public static final long DELETED_FILE_DATE = 0;
    public static final ContentHash DELETED_FILE_CHUNKS = new ContentHash(new byte[0]);

    public static final ContentHash EMPTY_FILE_CHUNKS = new ContentHash(new byte[0]);

    @Inject
    public BlockStorageDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    public void init_() throws SQLException
    {
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private PreparedStatement _psGetFileIndex;
    public long getFileIndex_(String iname) throws SQLException
    {
        PreparedStatement ps = _psGetFileIndex;
        try {
            if (ps == null) {
                ps = _psGetFileIndex = c().prepareStatement(
                        "SELECT " + C_FileInfo_Index +
                                " FROM " + T_FileInfo +
                                " WHERE " + C_FileInfo_InternalName + "=?");
            }

            ps.setString(1, iname);
            ResultSet rs = ps.executeQuery();

            try {
                if (!rs.next()) return FILE_ID_NOT_FOUND;
                return rs.getLong(1);
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetFileIndex = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    public long getOrCreateFileIndex_(String iname, Trans t) throws SQLException
    {
        long id = getFileIndex_(iname);
        if (id == FILE_ID_NOT_FOUND) id = createFileEntry_(iname, t);
        return id;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class FileInfo
    {
        public final long _id;
        public final long _ver;
        public final long _length;
        public final long _mtime;
        public final ContentHash _chunks;

        public boolean exists()
        {
            return _id != FILE_ID_NOT_FOUND && _length != DELETED_FILE_LEN;
        }

        public static boolean exists(FileInfo info)
        {
            return info != null && info.exists();
        }

        public FileInfo(long id, long ver, long length, long mtime, ContentHash chunks)
        {
            _id = id;
            _ver = ver;
            _length = length;
            _mtime = mtime;
            _chunks = chunks;
        }

        public static FileInfo newDeletedFileInfo(long id, long mtime)
        {
            return new FileInfo(id, -1, DELETED_FILE_LEN, mtime, DELETED_FILE_CHUNKS);
        }
    }

    private PreparedStatement _psGetFileInfo;
    public FileInfo getFileInfo_(long fileId) throws SQLException
    {
        PreparedStatement ps = _psGetFileInfo;
        try {
            if (ps == null) {
                ps = _psGetFileInfo = c().prepareStatement("SELECT " +
                        C_FileCurr_Ver + ',' +
                        C_FileCurr_Len + ',' +
                        C_FileCurr_Date + ',' +
                        C_FileCurr_Chunks +
                        " FROM " + T_FileCurr +
                        " WHERE " + C_FileCurr_Index + "=?");
            }
            ps.setLong(1, fileId);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) {
                    return null;
                } else {
                    byte[] hash = rs.getBytes(4);
                    // It appears that a byte[0] written into a SQLite db can come out
                    // as a null and ContentHash does not deal with that gracefully...
                    if (hash == null) {
                        hash = new byte[0];
                    }
                    return new FileInfo(
                            fileId,
                            rs.getLong(1),
                            rs.getLong(2),
                            rs.getLong(3),
                            new ContentHash(hash));
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetFileInfo = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private PreparedStatement _psGetChildHistDir;
    public long getChildHistDir_(long parent, String name) throws SQLException
    {
        PreparedStatement ps = _psGetChildHistDir;
        try {
            if (ps == null) {
                ps = _psGetChildHistDir = c().prepareStatement("SELECT " +
                        C_DirHist_Index + " FROM " + T_DirHist +
                        " WHERE " + C_DirHist_Parent + "=? AND " + C_DirHist_Name + "=?");
            }
            ps.setLong(1, parent);
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) {
                    return DIR_ID_NOT_FOUND;
                } else {
                    return rs.getLong(1);
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetChildHistDir = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    public long getOrCreateHistDirByPath_(Path path, Trans t) throws SQLException
    {
        long dirId = DIR_ID_ROOT;
        for (String name : path.elements()) dirId = getOrCreateChildHistDir_(dirId, name, t);
        return dirId;
    }

    public long getHistDirByPath_(Path path) throws SQLException
    {
        long dirId = DIR_ID_ROOT;
        for (String name : path.elements()) {
            long child = getChildHistDir_(dirId, name);
            if (child == DIR_ID_NOT_FOUND) return child;
            dirId = child;
        }
        return dirId;
    }

    private final PreparedStatementWrapper _pswDeleteHistDir = new PreparedStatementWrapper();
    public void deleteHistDir_(long dirId, Trans t) throws SQLException
    {
        final PreparedStatementWrapper psw = _pswDeleteHistDir;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement("DELETE FROM " + T_DirHist +
                        " WHERE " + C_DirHist_Index + "=?"));
            }
            ps.setLong(1, dirId);
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public Collection<Child> getHistDirChildren_(long dirId) throws SQLException {
        Set<Child> children = Sets.newHashSet();
        getHistDirChildFolders_(dirId, children);
        getHistDirChildFiles_(dirId, children);
        return children;
    }

    private PreparedStatement _psGetHistFileRevisions;
    public Collection<Revision> getHistFileRevisions_(long dirId, String name) throws SQLException
    {
        PreparedStatement ps = _psGetHistFileRevisions;
        try {
            if (ps == null) {
                ps = _psGetHistFileRevisions = c().prepareStatement("SELECT " +
                        C_FileHist_Index + ',' +
                        C_FileHist_Ver + ',' +
                        C_FileHist_Date + ',' +
                        C_FileHist_Len + " FROM " + T_FileHist +
                        " WHERE " + C_FileHist_Parent + "=? AND " + C_FileHist_RealName + "=?" +
                        " ORDER BY " + C_FileHist_Date);
            }
            ps.setLong(1, dirId);
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();
            List<Revision> revisions = Lists.newArrayList();
            try {
                while (rs.next()) {
                    byte[] index = encodeIndex(rs.getLong(1), rs.getLong(2));
                    if (index != null) {
                        revisions.add(new Revision(index, rs.getLong(3), rs.getLong(4)));
                    }
                }
            } finally {
                rs.close();
            }
            return revisions;
        } catch (SQLException e) {
            _psGetHistFileRevisions = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    private PreparedStatement _psGetHistFileInfo;
    public FileInfo getHistFileInfo_(byte[] index) throws SQLException
    {
        PreparedStatement ps = _psGetHistFileInfo;
        try {
            if (ps == null) {
                ps = _psGetHistFileInfo = c().prepareStatement("SELECT " +
                        C_FileHist_Len + ',' +
                        C_FileHist_Date + ',' +
                        C_FileHist_Chunks +
                        " FROM " + T_FileHist +
                        " WHERE " + C_FileHist_Index + "=? AND " + C_FileHist_Ver + "=?");
            }
            long[] idx = decodeIndex(index);
            if (idx == null) return null;

            ps.setLong(1, idx[0]);
            ps.setLong(2, idx[1]);
            ResultSet rs = ps.executeQuery();

            try {
                if (!rs.next()) {
                    return null;
                } else {
                    byte[] hash = rs.getBytes(3);
                    // It appears that a byte[0] written into a SQLite db can come out
                    // as a null and ContentHash does not deal with that gracefully...
                    if (hash == null) {
                        hash = new byte[0];
                    }
                    return new FileInfo(
                            idx[0],
                            idx[1],
                            rs.getLong(1),
                            rs.getLong(2),
                            new ContentHash(hash));
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetHistFileInfo = null;
            DBUtil.close(ps);
            throw e;
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////


    private PreparedStatementWrapper _pswPrePutBlock = new PreparedStatementWrapper();
    public void prePutBlock_(ContentHash chunk, long length, Trans t) throws SQLException
    {
        if (l.isDebugEnabled()) l.debug("start chunk upload: " + chunk);
        assert isOneBlock(chunk);
        PreparedStatementWrapper psw = _pswPrePutBlock;
        PreparedStatement ps = psw.get();
        try {
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        _dbcw.insertOrIgnore() + " into " + T_BlockCount + " (" +
                                C_BlockCount_Hash + ',' + C_BlockCount_Len + ',' +
                                C_BlockCount_State + ',' + C_BlockCount_Count +
                                ") VALUES(?,?,?,0)"));
            }

            ps.setBytes(1, chunk.getBytes());
            ps.setLong(2, length);
            ps.setInt(3, BlockState.STORING.sqlValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswPostPutBlock = new PreparedStatementWrapper();
    public void postPutBlock_(ContentHash chunk, Trans t) throws SQLException
    {
        if (l.isDebugEnabled()) l.debug("finish chunk upload: " + chunk);
        assert isOneBlock(chunk);
        PreparedStatementWrapper psw = _pswPostPutBlock;
        PreparedStatement ps = psw.get();
        try {
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement("update " + T_BlockCount +
                        " set " + C_BlockCount_State + "=?" +
                        " where " + C_BlockCount_Hash + "=?" +
                        " and " + C_BlockCount_State + "=?"));
            }

            ps.setInt(1, BlockState.STORED.sqlValue());
            ps.setBytes(2, chunk.getBytes());
            ps.setInt(3, BlockState.STORING.sqlValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }
    public void incBlockCount_(ContentHash chunk, Trans t) throws SQLException
    {
        adjustBlockCount_(chunk, 1, t);
    }

    public void decBlockCount_(ContentHash chunk, Trans t) throws SQLException
    {
        adjustBlockCount_(chunk, -1, t);
    }

    private PreparedStatementWrapper _pswGetChunkState = new PreparedStatementWrapper();
    public BlockState getBlockState_(ContentHash chunk) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetChunkState;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "select " + C_BlockCount_State + " from " + T_BlockCount +
                                " where " + C_BlockCount_Hash + "=?"));
            }

            ps.setBytes(1, chunk.getBytes());
            ResultSet rs = ps.executeQuery();

            try {
                if (!rs.next()) return null;
                else return BlockState.fromSql(rs.getInt(1));
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswGetChunkCount = new PreparedStatementWrapper();
    public long getBlockCount_(ContentHash chunk) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetChunkCount;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "select " + C_BlockCount_Count + " from " + T_BlockCount +
                                " where " + C_BlockCount_Hash + "=?"));
            }

            ps.setBytes(1, chunk.getBytes());
            ResultSet rs = ps.executeQuery();

            try {
                if (rs.next()) return rs.getLong(1);
                else return 0;
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatement _psDeleteBlock;
    public void deleteBlock_(ContentHash chunk, Trans t) throws SQLException
    {
        try {
            if (_psDeleteBlock == null) {
                _psDeleteBlock = c().prepareStatement(
                        "delete from " + T_BlockCount + " where " + C_BlockCount_Hash + "=?");
            }

            _psDeleteBlock.setBytes(1, chunk.getBytes());
            _psDeleteBlock.execute();
        } catch (SQLException e) {
            l.warn(Util.e(e));
            DBUtil.close(_psDeleteBlock);
            _psDeleteBlock =null;
            throw e;
        }
    }

    private PreparedStatement _psGetDeadBlocks;
    public IDBIterator<ContentHash> getDeadBlocks_() throws SQLException
    {
        try {
            if (_psGetDeadBlocks == null) {
                _psGetDeadBlocks = c().prepareStatement(
                        "select " + C_BlockCount_Hash +
                                " from " + T_BlockCount +
                                " where " + C_BlockCount_Count + "=0");
            }

            return new DBIterDeadBlocks(_psGetDeadBlocks.executeQuery());

        } catch (SQLException e) {
            l.warn(Util.e(e));
            DBUtil.close(_psGetDeadBlocks);
            _psGetDeadBlocks = null;
            throw e;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private PreparedStatement _psCreateChildHistDir;
    private long createChildHistDir_(long parent, String name, Trans t) throws SQLException
    {
        PreparedStatement ps = _psCreateChildHistDir;
        try {
            if (ps == null) {
                ps = _psCreateChildHistDir = c().prepareStatement("INSERT INTO " + T_DirHist + " (" +
                        C_DirHist_Parent + ',' +
                        C_DirHist_Name +
                        ") VALUES (?,?)", PreparedStatement.RETURN_GENERATED_KEYS);
            }
            ps.setLong(1, parent);
            ps.setString(2, name);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            try {
                if (!rs.next()) {
                    throw new SQLException("did not get index");
                } else {
                    return rs.getLong(1);
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psCreateChildHistDir = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    private long getOrCreateChildHistDir_(long parent, String name, Trans t) throws SQLException
    {
        long child = getChildHistDir_(parent, name);
        if (child == DIR_ID_NOT_FOUND) child = createChildHistDir_(parent, name, t);
        return child;
    }

    private PreparedStatement _psCreateFileEntry;
    private long createFileEntry_(String iname, Trans t) throws SQLException
    {
        PreparedStatement ps = _psCreateFileEntry;
        try {
            if (ps == null) {
                ps = _psCreateFileEntry = c().prepareStatement("INSERT INTO " + T_FileInfo + " ( " +
                        C_FileInfo_InternalName +
                        " ) VALUES (?)",PreparedStatement.RETURN_GENERATED_KEYS);
            }

            ps.setString(1, iname);
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            try {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    insertEmptyFileInfo(id, t);
                    return id;
                } else {
                    throw new SQLException("did not get index");
                }
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            _psCreateFileEntry = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    private PreparedStatementWrapper _pswInsertEmptyFileInfo = new PreparedStatementWrapper();
    private void insertEmptyFileInfo(long id, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswInsertEmptyFileInfo;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "INSERT INTO " + T_FileCurr + " ( " +
                                C_FileCurr_Index + ',' +
                                C_FileCurr_Ver + ',' +
                                C_FileCurr_Len + ',' +
                                C_FileCurr_Date + ',' +
                                C_FileCurr_Chunks +
                                " ) VALUES (?,?,?,?,?)"));
            }
            FileInfo info = FileInfo.newDeletedFileInfo(id, DELETED_FILE_DATE);
            ps.setLong(1, info._id);
            ps.setLong(2, 0);
            ps.setLong(3, info._length);
            ps.setLong(4, info._mtime);
            ps.setBytes(5, info._chunks.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswSaveOldFileInfo = new PreparedStatementWrapper();
    void saveOldFileInfo_(long dirId, String name, FileInfo info, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswSaveOldFileInfo;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "INSERT INTO " + T_FileHist + " (" +
                                C_FileHist_Index + ',' +
                                C_FileHist_Ver + ',' +
                                C_FileHist_Parent + ',' +
                                C_FileHist_RealName + ',' +
                                C_FileHist_Len + ',' +
                                C_FileHist_Date + ',' +
                                C_FileHist_Chunks + " ) " +
                                " VALUES (?,?,?,?,?,?,?)"));
            }

            ps.setLong(1, info._id);
            ps.setLong(2, info._ver);
            ps.setLong(3, dirId);
            ps.setString(4, name);
            ps.setLong(5, info._length);
            ps.setLong(6, info._mtime);
            ps.setBytes(7, info._chunks.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswWriteNewFileInfo = new PreparedStatementWrapper();
    void writeNewFileInfo_(FileInfo info, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswWriteNewFileInfo;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "UPDATE " + T_FileCurr + " SET " +
                                C_FileCurr_Ver + "=" + C_FileCurr_Ver + "+1, " +
                                C_FileCurr_Len + "=?, " +
                                C_FileCurr_Date + "=?, " +
                                C_FileCurr_Chunks + "=? WHERE " +
                                C_FileCurr_Index + "=?"));
            }

            ps.setLong(1, info._length);
            ps.setLong(2, info._mtime);
            ps.setBytes(3, info._chunks.getBytes());
            ps.setLong(4, info._id);
            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated != 1) {
                throw new SQLException("Updated " + rowsUpdated + " rows, expected 1");
            }
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatement _psGetHistDirChildFolders;
    private void getHistDirChildFolders_(long dirId, Set<? super Child> children)
            throws SQLException
    {
        PreparedStatement ps = _psGetHistDirChildFolders;
        try {
            if (ps == null) {
                ps = _psGetHistDirChildFolders = c().prepareStatement("SELECT " +
                        C_DirHist_Name + " FROM " + T_DirHist +
                        " WHERE " + C_DirHist_Parent + "=?");
            }
            ps.setLong(1, dirId);
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    children.add(new Child(rs.getString(1), true));
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetHistDirChildFolders = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    private PreparedStatement _psGetHistDirChildFiles;
    private void getHistDirChildFiles_(long dirId, Set<? super Child> children) throws SQLException
    {
        PreparedStatement ps = _psGetHistDirChildFiles;
        try {
            if (ps == null) {
                ps = _psGetHistDirChildFiles = c().prepareStatement("SELECT " +
                        C_FileHist_RealName + " FROM " + T_FileHist +
                        " WHERE " + C_FileHist_Parent + "=?");
            }
            ps.setLong(1, dirId);
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    children.add(new Child(rs.getString(1), false));
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetHistDirChildFiles = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    private byte[] encodeIndex(long id, long version)
    {
        return Util.string2utf(
                Util.hexEncode(ByteBuffer.allocate(16).putLong(id).putLong(version).array()));
    }

    private long[] decodeIndex(byte[] index) {
        if (index.length != 32) return null;
        try {
            ByteBuffer buf = ByteBuffer.wrap(Util.hexDecode(Util.utf2string(index)));
            long[] result = new long[2];
            result[0] = buf.getLong();
            result[1] = buf.getLong();
            return result;
        } catch (ExFormatError e) {
            return null;
        }
    }

    private PreparedStatementWrapper _pswAdjustBlockCount = new PreparedStatementWrapper();
    private void adjustBlockCount_(ContentHash chunk, int delta, Trans t) throws SQLException
    {
        assert isOneBlock(chunk);
        PreparedStatementWrapper psw = _pswAdjustBlockCount;
        PreparedStatement ps = psw.get();
        try {
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement("update " + T_BlockCount +
                        " set " + C_BlockCount_State + "=?, " +
                        C_BlockCount_Count + "=" + C_BlockCount_Count + "+?" +
                        " where " + C_BlockCount_Hash + "=?"));
            }

            ps.setInt(1, BlockState.REFERENCED.sqlValue());
            ps.setInt(2, delta);
            ps.setBytes(3, chunk.getBytes());
            int rows = ps.executeUpdate();
            assert rows == 1;
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private static class DBIterDeadBlocks extends AbstractDBIterator<ContentHash>
    {
        public DBIterDeadBlocks(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public ContentHash get_() throws SQLException
        {
            return new ContentHash(_rs.getBytes(1));
        }
    }
}
