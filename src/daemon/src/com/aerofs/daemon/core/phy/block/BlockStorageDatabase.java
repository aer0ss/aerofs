/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.base.ex.ExFormatError;
import com.google.inject.Inject;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.aerofs.daemon.core.phy.block.BlockStorageSchema.*;
import static com.aerofs.daemon.core.phy.block.BlockUtil.isOneBlock;
import static com.aerofs.daemon.core.phy.block.BlockUtil.splitBlocks;
import static com.google.common.base.Preconditions.checkArgument;

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
    private static final Logger l = Loggers.getLogger(BlockStorageDatabase.class);

    public static final long FILE_ID_NOT_FOUND = -1;

    public static final long DIR_ID_NOT_FOUND = -1;
    public static final long DIR_ID_ROOT = -2;

    public static final long DELETED_FILE_LEN = -1; //C.S3_DELETED_FILE_LEN;
    public static final long DELETED_FILE_DATE = 0;
    public static final ContentBlockHash DELETED_FILE_CHUNKS = new ContentBlockHash(new byte[0]);

    public static final ContentBlockHash EMPTY_FILE_CHUNKS = new ContentBlockHash(new byte[0]);

    public BlockStorageDatabase(IDBCW dbcw)
    {
        super(dbcw);
    }

    @Inject
    public BlockStorageDatabase(CoreDBCW dbcw)
    {
        super(dbcw.get());
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
                ps = _psGetFileIndex = c().prepareStatement(DBUtil.selectWhere(T_FileInfo,
                        C_FileInfo_InternalName + "=?",
                        C_FileInfo_Index));
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
            throw detectCorruption(e);
        }
    }

    public long getOrCreateFileIndex_(String iname, Trans t) throws SQLException
    {
        long id = getFileIndex_(iname);
        if (id == FILE_ID_NOT_FOUND) id = createFileEntry_(iname, t);
        return id;
    }

    private PreparedStatement _psGetIndices;
    IDBIterator<Long> getIndicesWithPrefix_(String prefix) throws SQLException
    {
        PreparedStatement ps = _psGetIndices;
        try {
            if (ps == null) {
                ps = _psGetIndices = c().prepareStatement(DBUtil.selectWhere(T_FileInfo,
                        C_FileInfo_InternalName + " GLOB \"" + prefix + "*\"",
                        C_FileInfo_Index));
            }

            return new DBIterIndices(ps.executeQuery());
        } catch (SQLException e) {
            _psGetIndices = null;
            DBUtil.close(ps);
            throw detectCorruption(e);
        }
    }

    class DBIterIndices extends AbstractDBIterator<Long>
    {
        DBIterIndices(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public Long get_() throws SQLException
        {
            return _rs.getLong(1);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class FileInfo
    {
        public final long _id;
        public final long _ver;
        public final long _length;
        public final long _mtime;
        public final ContentBlockHash _chunks;

        public boolean exists()
        {
            return _id != FILE_ID_NOT_FOUND && _length != DELETED_FILE_LEN;
        }

        public static boolean exists(FileInfo info)
        {
            return info != null && info.exists();
        }

        public FileInfo(long id, long ver, long length, long mtime, ContentBlockHash chunks)
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
    public @Nullable FileInfo getFileInfo_(long fileId) throws SQLException
    {
        PreparedStatement ps = _psGetFileInfo;
        try {
            if (ps == null) {
                ps = _psGetFileInfo = c().prepareStatement(DBUtil.selectWhere(T_FileCurr,
                        C_FileCurr_Index + "=?",
                        C_FileCurr_Ver, C_FileCurr_Len, C_FileCurr_Date, C_FileCurr_Chunks));
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
                            new ContentBlockHash(hash));
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetFileInfo = null;
            DBUtil.close(ps);
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswDelFileInfo = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_FileCurr, C_FileCurr_Index + "=?"));
    public void deleteFileInfo_(long fileId, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswDelFileInfo.get(c());
            ps.setLong(1, fileId);
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswDelFileInfo.close();
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswDelHistFileInfo = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_FileHist, C_FileHist_Index + "=? and " + C_FileHist_Ver + "=?"));
    public void deleteHistFileInfo_(long fileId, long ver, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswDelHistFileInfo.get(c());
            ps.setLong(1, fileId);
            ps.setLong(2, ver);
            ps.executeUpdate();
        } catch (SQLException e) {
            _pswDelHistFileInfo.close();
            throw detectCorruption(e);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private PreparedStatement _psGetChildHistDir;
    public long getChildHistDir_(long parent, String name) throws SQLException
    {
        PreparedStatement ps = _psGetChildHistDir;
        try {
            if (ps == null) {
                ps = _psGetChildHistDir = c().prepareStatement(DBUtil.selectWhere(T_DirHist,
                        C_DirHist_Parent + "=? AND " + C_DirHist_Name + "=?",
                        C_DirHist_Index));
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
            throw detectCorruption(e);
        }
    }

    public long getOrCreateHistDirByPath_(Path path, Trans t) throws SQLException
    {
        long dirId = getOrCreateChildHistDir_(DIR_ID_ROOT, path.sid().toStringFormal(), t);
        for (String name : path.elements()) dirId = getOrCreateChildHistDir_(dirId, name, t);
        return dirId;
    }

    public long getHistDirByPath_(Path path) throws SQLException
    {
        long dirId = getChildHistDir_(DIR_ID_ROOT, path.sid().toStringFormal());
        for (String name : path.elements()) {
            long child = getChildHistDir_(dirId, name);
            if (child == DIR_ID_NOT_FOUND) return child;
            dirId = child;
        }
        return dirId;
    }

    private final PreparedStatementWrapper _pswDeleteHistDir = new PreparedStatementWrapper(
            DBUtil.deleteWhere(T_DirHist, C_DirHist_Index + "=?"));
    public void deleteHistDir_(long dirId, Trans t) throws SQLException
    {
        final PreparedStatementWrapper psw = _pswDeleteHistDir;
        try {
            PreparedStatement ps = psw.get(c());
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
                        " ORDER BY " + C_FileHist_Ver);
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
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGetHistFileInfo;
    public FileInfo getHistFileInfo_(byte[] index) throws SQLException
    {
        PreparedStatement ps = _psGetHistFileInfo;
        try {
            if (ps == null) {
                ps = _psGetHistFileInfo = c().prepareStatement(DBUtil.selectWhere(T_FileHist,
                        C_FileHist_Index + "=? AND " + C_FileHist_Ver + "=?",
                        C_FileHist_Len, C_FileHist_Date, C_FileHist_Chunks));
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
                            new ContentBlockHash(hash));
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetHistFileInfo = null;
            DBUtil.close(ps);
            throw detectCorruption(e);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////


    private PreparedStatementWrapper _pswPrePutBlock = new PreparedStatementWrapper(
            _dbcw.insertOrIgnore() + " into " + T_BlockCount + " (" +
                    C_BlockCount_Hash + ',' + C_BlockCount_Len + ',' +
                    C_BlockCount_State + ',' + C_BlockCount_Count +
                    ") VALUES(?,?,?,0)");
    public void prePutBlock_(ContentBlockHash chunk, long length, Trans t) throws SQLException
    {
        l.debug("start chunk upload: {}", chunk);
        checkArgument(isOneBlock(chunk));
        PreparedStatementWrapper psw = _pswPrePutBlock;
        try {
            PreparedStatement ps = psw.get(c());
            ps.setBytes(1, chunk.getBytes());
            ps.setLong(2, length);
            ps.setInt(3, BlockState.STORING.sqlValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswPostPutBlock = new PreparedStatementWrapper(
            "update " + T_BlockCount +
                    " set " + C_BlockCount_State + "=?" +
                    " where " + C_BlockCount_Hash + "=?" +
                    " and " + C_BlockCount_State + "=?");
    public void postPutBlock_(ContentBlockHash chunk, Trans t) throws SQLException
    {
        l.debug("finish chunk upload: {}", chunk);
        checkArgument(isOneBlock(chunk));
        PreparedStatementWrapper psw = _pswPostPutBlock;
        try {
            PreparedStatement ps = psw.get(c());
            ps.setInt(1, BlockState.STORED.sqlValue());
            ps.setBytes(2, chunk.getBytes());
            ps.setInt(3, BlockState.STORING.sqlValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }
    public void incBlockCount_(ContentBlockHash chunk, Trans t) throws SQLException
    {
        adjustBlockCount_(chunk, 1, t);
    }

    public void decBlockCount_(ContentBlockHash chunk, Trans t) throws SQLException
    {
        adjustBlockCount_(chunk, -1, t);
    }

    private PreparedStatementWrapper _pswGetChunkState = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_BlockCount, C_BlockCount_Hash + "=?", C_BlockCount_State));
    public BlockState getBlockState_(ContentBlockHash chunk) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetChunkState;
        try {
            PreparedStatement ps = psw.get(c());
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
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswGetChunkCount = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_BlockCount, C_BlockCount_Hash + "=?",  C_BlockCount_Count));
    public long getBlockCount_(ContentBlockHash chunk) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetChunkCount;
        try {
            PreparedStatement ps = psw.get(c());
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
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psDeleteBlock;
    public void deleteBlock_(ContentBlockHash chunk, Trans t) throws SQLException
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
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGetDeadBlocks;
    public IDBIterator<ContentBlockHash> getDeadBlocks_() throws SQLException
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
            throw detectCorruption(e);
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
            throw detectCorruption(e);
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
            throw detectCorruption(e);
        }
    }

    private final PreparedStatementWrapper _pswAliasFileEntry = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_FileInfo, C_FileInfo_InternalName + "=?", C_FileInfo_InternalName));
    void updateInternalName_(String internalName, String newInternalName, Trans t) throws SQLException
    {
        try {
            PreparedStatement ps = _pswAliasFileEntry.get(c());
            ps.setString(1, newInternalName);
            ps.setString(2, internalName);
            Util.verify(ps.executeUpdate() == 1);
        } catch (SQLException e) {
            _pswAliasFileEntry.close();
            throw detectCorruption(e);
        }
    }

    /**
     * Hist File Info entry may remain after deletion of the related File Info entry and said File
     * Info entry may be recreated later before the Hist File Info have been cleaned by a garbage
     * collector. This means that when recreating the File Info entry when need to initialize the
     * version to a value above the maximum of existing Hist File Info entries, hence the existence
     * of this function
     *
     * @return max version number for the given file index, -1 if no matching hist entry found
     */
    private PreparedStatementWrapper _pswGetMaxVersion = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_FileHist, C_FileHist_Index + "=?", "max(" + C_FileHist_Ver + ")"));
    private long getMaxHistVersion(long id) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetMaxVersion;
        try {
            PreparedStatement ps = psw.get(c());
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            try {
                if (rs.next()) return rs.getLong(1);
                return -1;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswInsertEmptyFileInfo = new PreparedStatementWrapper(
            DBUtil.insert(T_FileCurr, C_FileCurr_Index, C_FileCurr_Ver, C_FileCurr_Len,
                    C_FileCurr_Date, C_FileCurr_Chunks));
    private void insertEmptyFileInfo(long id, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswInsertEmptyFileInfo;
        try {
            PreparedStatement ps = psw.get(c());
            FileInfo info = FileInfo.newDeletedFileInfo(id, DELETED_FILE_DATE);
            ps.setLong(1, info._id);
            ps.setLong(2, getMaxHistVersion(id) + 1);
            ps.setLong(3, info._length);
            ps.setLong(4, info._mtime);
            ps.setBytes(5, info._chunks.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    /**
     * If the "existingFile" argument exists, back it up in the history. This will
     * create the history hierarchy as needed.
     * If the file does not exist, this is a no-op.
     * Note there are no changes to ref counts for chunks used by existingFile.
     */
    void preserveFileInfo(Path path, FileInfo existingFile, Trans t) throws SQLException
    {
        if (FileInfo.exists(existingFile)) {
            long dirId = getOrCreateHistDirByPath_(path.removeLast(), t);
            saveOldFileInfo_(dirId, path.last(), existingFile, t);
        }
    }

    /**
     * Update file info after successful file update
     *
     * If the current file info is valid, back it up in the history, creating hierarchy as neeeded
     * Increment ref count for chunks used by the new file info
     */
    void updateFileInfo(Path path, FileInfo info, Trans t) throws SQLException
    {
        // update file info
        writeNewFileInfo_(info, t);
        for (ContentBlockHash chunk : splitBlocks(info._chunks)) {
            incBlockCount_(chunk, t);
        }
    }

    private PreparedStatementWrapper _pswSaveOldFileInfo = new PreparedStatementWrapper(
            DBUtil.insert(T_FileHist, C_FileHist_Index, C_FileHist_Ver, C_FileHist_Parent,
                    C_FileHist_RealName, C_FileHist_Len, C_FileHist_Date, C_FileHist_Chunks));
    private void saveOldFileInfo_(long dirId, String name, FileInfo info, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswSaveOldFileInfo;
        try {
            PreparedStatement ps = psw.get(c());
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
            throw detectCorruption(e);
        }
    }

    private PreparedStatementWrapper _pswWriteNewFileInfo = new PreparedStatementWrapper(
            "UPDATE " + T_FileCurr + " SET "
                    + C_FileCurr_Ver + "=" + C_FileCurr_Ver + "+1, " + C_FileCurr_Len + "=?, "
                    + C_FileCurr_Date + "=?, " +  C_FileCurr_Chunks + "=?"
                    + " WHERE " + C_FileCurr_Index + "=?");
    private void writeNewFileInfo_(FileInfo info, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswWriteNewFileInfo;
        try {
            PreparedStatement ps = psw.get(c());
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
            throw detectCorruption(e);
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
            throw detectCorruption(e);
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
            throw detectCorruption(e);
        }
    }

    private byte[] encodeIndex(long id, long version)
    {
        return BaseUtil.string2utf(
                BaseUtil.hexEncode(ByteBuffer.allocate(16).putLong(id).putLong(version).array()));
    }

    private long[] decodeIndex(byte[] index) {
        if (index.length != 32) return null;
        try {
            ByteBuffer buf = ByteBuffer.wrap(BaseUtil.hexDecode(BaseUtil.utf2string(index)));
            long[] result = new long[2];
            result[0] = buf.getLong();
            result[1] = buf.getLong();
            return result;
        } catch (ExFormatError e) {
            return null;
        }
    }

    private PreparedStatementWrapper _pswAdjustBlockCount = new PreparedStatementWrapper(
            "update " + T_BlockCount + " set "
                    + C_BlockCount_State + "=?, "
                    + C_BlockCount_Count + "=" + C_BlockCount_Count + "+?"
                    + " where " + C_BlockCount_Hash + "=?");
    private void adjustBlockCount_(ContentBlockHash chunk, int delta, Trans t) throws SQLException
    {
        assert isOneBlock(chunk);
        PreparedStatementWrapper psw = _pswAdjustBlockCount;
        try {
            PreparedStatement ps = psw.get(c());
            ps.setInt(1, BlockState.REFERENCED.sqlValue());
            ps.setInt(2, delta);
            ps.setBytes(3, chunk.getBytes());
            int rows = ps.executeUpdate();
            assert rows == 1;
        } catch (SQLException e) {
            psw.close();
            throw detectCorruption(e);
        }
    }

    private static class DBIterDeadBlocks extends AbstractDBIterator<ContentBlockHash>
    {
        public DBIterDeadBlocks(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public ContentBlockHash get_() throws SQLException
        {
            return new ContentBlockHash(_rs.getBytes(1));
        }
    }
}
