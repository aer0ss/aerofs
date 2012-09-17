package com.aerofs.lib.aws.s3.db;

import static com.aerofs.lib.db.S3Schema.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aerofs.lib.db.PreparedStatementWrapper;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Child;
import com.aerofs.daemon.core.phy.IPhysicalRevProvider.Revision;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.CoreDBCW;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.C;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.aws.s3.chunks.S3ChunkAccessor;
import com.aerofs.lib.db.AbstractDBIterator;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.db.S3Schema.ChunkState;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;

public class S3Database extends AbstractDatabase
{
    private static final Logger l = Util.l(S3Database.class);

    public static final long FILE_ID_NOT_FOUND = -1;

    public static final long DIR_ID_NOT_FOUND = -1;
    public static final long DIR_ID_ROOT = -2;

    public static final long DELETED_FILE_LEN = C.S3_DELETED_FILE_LEN;
    public static final long DELETED_FILE_DATE = 0;
    public static final ContentHash DELETED_FILE_CHUNKS = new ContentHash(new byte[0]);

    public static final ContentHash EMPTY_FILE_CHUNKS = new ContentHash(new byte[0]);

    public S3Database(IDBCW dbcw) throws SQLException
    {
        super(dbcw);
    }

    @Inject
    public S3Database(CoreDBCW coreDBCW) throws SQLException
    {
        this(coreDBCW.get());
    }

    public void init_() throws SQLException
    {
    }

    private PreparedStatement _psGetChildDir;
    public long getChildDir_(long parent, String name) throws SQLException
    {
        PreparedStatement ps = _psGetChildDir;
        try {
            if (ps == null) {
                ps = _psGetChildDir = c().prepareStatement("SELECT " +
                        C_DirCurr_Index + " FROM " + T_DirCurr +
                        " WHERE " + C_DirCurr_Parent + "=? AND " + C_DirCurr_Name + "=?");
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
            _psGetChildDir = null;
            DBUtil.close(ps);
            throw e;
        }
    }

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
        if (child == DIR_ID_NOT_FOUND) {
            child = createChildHistDir_(parent, name, t);
        }
        return child;
    }

    private long getDirHistIndex_(long dirId) throws SQLException
    {
        long histId;
        if (dirId == DIR_ID_ROOT) {
            histId = DIR_ID_ROOT;
        } else {
            S3DirInfo info = getDirInfo_(dirId);
            histId = info._histIndex;
        }
        return histId;
    }

    private PreparedStatement _psCreateChildDir;
    public long createChildDir_(long parent, String name, Trans t) throws SQLException, ExAlreadyExist
    {
        long histParent = getDirHistIndex_(parent);
        long histChild = getOrCreateChildHistDir_(histParent, name, t);

        PreparedStatement ps = _psCreateChildDir;
        try {
            if (ps == null) {
                ps = _psCreateChildDir = c().prepareStatement("INSERT INTO " + T_DirCurr + " (" +
                        C_DirCurr_HistIndex + ',' +
                        C_DirCurr_Parent + ',' +
                        C_DirCurr_Name +
                        ") VALUES (?,?,?)", PreparedStatement.RETURN_GENERATED_KEYS);
            }
            ps.setLong(1, histChild);
            ps.setLong(2, parent);
            ps.setString(3, name);
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
            _dbcw.throwOnConstraintViolation(e);
            DBUtil.close(_psCreateChildDir);
            _psCreateChildDir = null;
            throw e;
        }
    }

    private final PreparedStatementWrapper _pswDeleteDir = new PreparedStatementWrapper();
    public void deleteDir_(long dirId, Trans t) throws SQLException
    {
        final PreparedStatementWrapper psw = _pswDeleteDir;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement("DELETE FROM " + T_DirCurr +
                        " WHERE " + C_DirCurr_Index + "=?"));
            }
            ps.setLong(1, dirId);
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
        }
    }

    private PreparedStatement _psSetDirInfo;
    public void setDirParentAndName_(long dirId, long parent, String name, Trans t) throws SQLException
    {
        long histParent = getDirHistIndex_(parent);
        long histChild = getOrCreateChildHistDir_(histParent, name, t);
        PreparedStatement ps = _psSetDirInfo;
        try {
            if (ps == null) {
                ps = _psSetDirInfo = c().prepareStatement("UPDATE " + T_DirCurr + " SET " +
                        C_DirCurr_HistIndex + "=?, " +
                        C_DirCurr_Parent + "=?, " +
                        C_DirCurr_Name + "=? WHERE " +
                        C_DirCurr_Index + "=?");
            }
            ps.setLong(1, histChild);
            ps.setLong(2, parent);
            ps.setString(3, name);
            ps.setLong(4, dirId);
            ps.executeUpdate();
        } catch (SQLException e) {
            _psSetDirInfo = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    public static class S3DirInfo
    {
        public long _id;
        public long _histIndex;
        public long _parent;
        public String _name;
    }

    private PreparedStatement _psGetDirInfo;
    public S3DirInfo getDirInfo_(long dirId) throws SQLException
    {
        PreparedStatement ps = _psGetDirInfo;
        try {
            if (ps == null) {
                ps = _psGetDirInfo = c().prepareStatement("SELECT " +
                        C_DirCurr_HistIndex + ',' + C_DirCurr_Parent + ',' + C_DirCurr_Name +
                        " FROM " + T_DirCurr +
                        " WHERE " + C_DirCurr_Index + "=?");
            }
            ps.setLong(1, dirId);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) {
                    return null;
                } else {
                    S3DirInfo info = new S3DirInfo();
                    info._id = dirId;
                    info._histIndex = rs.getLong(1);
                    info._parent = rs.getLong(2);
                    info._name = rs.getString(3);
                    return info;
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetDirInfo = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    public long getOrCreateFileIndex_(SIndex sidx, String iname, Trans t)
            throws SQLException
    {
        long id = getFileIndex_(sidx, iname);
        if (id == FILE_ID_NOT_FOUND) {
            id = createFileEntry_(sidx, iname, t);
        }
        return id;
    }

    private PreparedStatement _psCreateFileEntry;
    private long createFileEntry_(SIndex sidx, String iname, Trans t)
            throws SQLException
    {
        PreparedStatement ps = _psCreateFileEntry;
        try {
            if (ps == null) {
                ps = _psCreateFileEntry = c().prepareStatement("INSERT INTO " + T_FileInfo + " ( " +
                        C_FileInfo_InternalName + "," +
                        C_FileInfo_Store +
                        " ) VALUES (?,?)",PreparedStatement.RETURN_GENERATED_KEYS);
            }

            ps.setString(1, iname);
            ps.setInt(2, sidx.getInt());
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            try {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    insertEmptyFileInfo(id, sidx, t);
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

    private PreparedStatement _psGetFileIndex;
    public long getFileIndex_(SIndex sidx, String iname)
            throws SQLException
    {
        PreparedStatement ps = _psGetFileIndex;
        try {
            if (ps == null) {
                ps = _psGetFileIndex = c().prepareStatement(
                        "SELECT " + C_FileInfo_Index +
                        " FROM " + T_FileInfo +
                        " WHERE " + C_FileInfo_Store + "=? AND " + C_FileInfo_InternalName + "=?");
            }

            ps.setInt(1, sidx.getInt());
            ps.setString(2, iname);
            ResultSet rs = ps.executeQuery();

            try {
                if (!rs.next()) return FILE_ID_NOT_FOUND;
                long id = rs.getLong(1);
                return id;
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetFileIndex = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    private PreparedStatement _psGetChildFile;
    public long getChildFile_(long dirId, String name) throws SQLException
    {
        PreparedStatement ps = _psGetChildFile;
        try {
            if (ps == null) {
                ps = _psGetChildFile = c().prepareStatement("SELECT " +
                        C_FileCurr_Index + " FROM " + T_FileCurr +
                        " WHERE " + C_FileCurr_Parent + "=? AND " + C_FileCurr_RealName + "=?");
            }
            ps.setLong(1, dirId);
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();
            try {
                if (!rs.next()) {
                    return FILE_ID_NOT_FOUND;
                } else {
                    return rs.getLong(1);
                }
            } finally {
                rs.close();
            }
        } catch (SQLException e) {
            _psGetChildFile = null;
            DBUtil.close(ps);
            throw e;
        }
    }

    public static class S3FileInfo
    {
        public final long _id;
        public final SIndex _sidx;
        public long _parent;
        public String _name;
        public long _length;
        public Date _date;
        public ContentHash _chunks;

        public boolean exists()
        {
            return _id != FILE_ID_NOT_FOUND && _length != DELETED_FILE_LEN;
        }

        public static boolean exists(S3FileInfo info)
        {
            return info != null && info.exists();
        }

        public S3FileInfo(long id, SIndex sidx, long parent, String name,
                long length, Date date, ContentHash chunks)
        {
            _id = id;
            _sidx = sidx;
            _parent = parent;
            _name = name;
            _length = length;
            _date = date;
            _chunks = chunks;
        }

        public static S3FileInfo newDeletedFileInfo(long id, SIndex sidx, Date date)
        {
            if (date == null) date = new Date(DELETED_FILE_DATE);
            S3FileInfo info = new S3FileInfo(id, sidx, DIR_ID_NOT_FOUND, null,
                    DELETED_FILE_LEN, date, DELETED_FILE_CHUNKS);
            return info;
        }
    }

    private PreparedStatement _psGetFileInfo;
    public S3FileInfo getFileInfo_(long fileId) throws SQLException
    {
        PreparedStatement ps = _psGetFileInfo;
        try {
            if (ps == null) {
                ps = _psGetFileInfo = c().prepareStatement("SELECT " +
                        C_FileCurr_Store + ',' +
                        C_FileCurr_Parent + ',' +
                        C_FileCurr_RealName + ',' +
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
                    byte[] hash = rs.getBytes(6);
                    // It appears that a byte[0] written into a SQLite db can come out
                    // as a null and ContentHash does not deal with that gracefully...
                    if (hash == null) {
                        hash = new byte[0];
                    }
                    S3FileInfo info = new S3FileInfo(
                            fileId,
                            new SIndex(rs.getInt(1)),
                            rs.getLong(2),
                            rs.getString(3),
                            rs.getLong(4),
                            new Date(rs.getLong(5)),
                            new ContentHash(hash));
                    return info;
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

    private PreparedStatementWrapper _pswInsertEmptyFileInfo = new PreparedStatementWrapper();
    private void insertEmptyFileInfo(long id, SIndex sidx, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswInsertEmptyFileInfo;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "INSERT INTO " + T_FileCurr + " ( " +
                                C_FileCurr_Index + ',' +
                                C_FileCurr_Store + ',' +
                                C_FileCurr_Ver + ',' +
                                C_FileCurr_Parent + ',' +
                                C_FileCurr_RealName + ',' +
                                C_FileCurr_Len + ',' +
                                C_FileCurr_Date + ',' +
                                C_FileCurr_Chunks +
                                " ) VALUES (?,?,?,?,?,?,?,?)"));
            }
            S3FileInfo info = S3FileInfo.newDeletedFileInfo(id, sidx, null);
            ps.setLong(1, info._id);
            ps.setInt(2, info._sidx.getInt());
            ps.setLong(3, 0);
            ps.setLong(4, info._parent);
            ps.setString(5, info._name);
            ps.setLong(6, info._length);
            ps.setLong(7, info._date.getTime());
            ps.setBytes(8, info._chunks.getBytes());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    public void updateFileInfo(S3FileInfo info, Trans t) throws SQLException
    {
        saveOldFileInfo(info._id, t);
        writeNewFileInfo(info, t);
    }

    private PreparedStatementWrapper _pswSaveOldFileInfo = new PreparedStatementWrapper();
    private void saveOldFileInfo(long id, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswSaveOldFileInfo;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "INSERT INTO " + T_FileHist + " (" +
                                C_FileHist_Index + ',' +
                                C_FileHist_Store + ',' +
                                C_FileHist_Ver + ',' +
                                C_FileHist_Parent + ',' +
                                C_FileHist_RealName + ',' +
                                C_FileHist_Len + ',' +
                                C_FileHist_Date + ',' +
                                C_FileHist_Chunks + " ) " +
                                "SELECT " +
                                C_FileCurr_Index + ',' +
                                C_FileCurr_Store + ',' +
                                C_FileCurr_Ver + ',' +
                                "COALESCE(" + C_DirCurr_HistIndex + "," + DIR_ID_ROOT + ")," +
                                C_FileCurr_RealName + ',' +
                                C_FileCurr_Len + ',' +
                                C_FileCurr_Date + ',' +
                                C_FileCurr_Chunks +
                                " FROM " + T_FileCurr +
                                " LEFT JOIN " + T_DirCurr +
                                " ON " + C_FileCurr_Parent + '=' + C_DirCurr_Index +
                                " WHERE " + C_FileCurr_Index + "=?"));
            }

            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswWriteNewFileInfo = new PreparedStatementWrapper();
    private void writeNewFileInfo(S3FileInfo info, Trans t) throws SQLException
    {
        PreparedStatementWrapper psw = _pswWriteNewFileInfo;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "UPDATE " + T_FileCurr + " SET " +
                                C_FileCurr_Ver + "=" + C_FileCurr_Ver + "+1, " +
                                C_FileCurr_Parent + "=?, " +
                                C_FileCurr_RealName + "=?, " +
                                C_FileCurr_Len + "=?, " +
                                C_FileCurr_Date + "=?, " +
                                C_FileCurr_Chunks + "=? WHERE " +
                                C_FileCurr_Index + "=?"));
            }

            ps.setLong(1, info._parent);
            ps.setString(2, info._name);
            ps.setLong(3, info._length);
            ps.setLong(4, info._date.getTime());
            ps.setBytes(5, info._chunks.getBytes());
            ps.setLong(6, info._id);
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
    private void getHistDirChildFolders_(long dirId, Set<? super Child> children) throws SQLException
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

    public Collection<Child> getHistDirChildren_(long dirId) throws SQLException {
        Set<Child> children = new HashSet<Child>();
        getHistDirChildFolders_(dirId, children);
        getHistDirChildFiles_(dirId, children);
        return children;
    }

    private byte[] encodeVersion(long version) {
        return Util.string2utf(String.valueOf(version));
    }

    private long decodeVersion(byte[] index) {
        return Long.valueOf(Util.utf2string(index));
    }

    private PreparedStatement _psGetHistFileRevisions;
    public Collection<Revision> getHistFileRevisions_(long dirId, String name) throws SQLException
    {
        PreparedStatement ps = _psGetHistFileRevisions;
        try {
            if (ps == null) {
                ps = _psGetHistFileRevisions = c().prepareStatement("SELECT " +
                        C_FileHist_Ver + ',' +
                        C_FileHist_Date + ',' +
                        C_FileHist_Len + " FROM " + T_FileHist +
                        " WHERE " + C_FileHist_Parent + "=? AND " + C_FileHist_RealName + "=?" +
                        " ORDER BY " + C_FileHist_Date);
            }
            ps.setLong(1, dirId);
            ps.setString(2, name);
            ResultSet rs = ps.executeQuery();
            List<Revision> revisions = new ArrayList<Revision>();
            try {
                while (rs.next()) {
                    byte[] index = encodeVersion(rs.getLong(1));
                    if (index != null) {
                        revisions.add(new Revision(index, rs.getLong(2), rs.getLong(3)));
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
    public S3FileInfo getHistFileInfo_(long dirId, String name, byte[] index) throws SQLException
    {
        PreparedStatement ps = _psGetHistFileInfo;
        try {
            if (ps == null) {
                ps = _psGetHistFileInfo = c().prepareStatement("SELECT " +
                        C_FileHist_Index + ',' +
                        C_FileHist_Store + ',' +
                        C_FileHist_Len + ',' +
                        C_FileHist_Date + ',' +
                        C_FileHist_Chunks +
                        " FROM " + T_FileHist +
                        " WHERE " + C_FileHist_Parent + "=? AND " +
                        C_FileHist_RealName + "=? AND " + C_FileHist_Ver + "=?");
            }
            ps.setLong(1, dirId);
            ps.setString(2, name);
            ps.setLong(3, decodeVersion(index));
            ResultSet rs = ps.executeQuery();

            try {
                if (!rs.next()) {
                    return null;
                } else {
                    byte[] hash = rs.getBytes(5);
                    // It appears that a byte[0] written into a SQLite db can come out
                    // as a null and ContentHash does not deal with that gracefully...
                    if (hash == null) {
                        hash = new byte[0];
                    }
                    S3FileInfo info = new S3FileInfo(
                            rs.getLong(1),
                            new SIndex(rs.getInt(2)),
                            dirId,
                            name,
                            rs.getLong(3),
                            new Date(rs.getLong(4)),
                            new ContentHash(hash));
                    return info;
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

    // =============== chunk count ==================

    private static class DBIterChunks extends AbstractDBIterator<ContentHash>
    {
        public DBIterChunks(ResultSet rs)
        {
            super(rs);
        }

        @Override
        public ContentHash get_() throws SQLException
        {
            return new ContentHash(_rs.getBytes(1));
        }
    }

    private PreparedStatement _psGetAllChunks;
    public IDBIterator<ContentHash> getAllChunks() throws SQLException
    {
        try {
            if (_psGetAllChunks == null) {
                _psGetAllChunks = c().prepareStatement("select " + C_ChunkCount_Hash +
                        " from " + T_ChunkCount
                        );
            }

            return new DBIterChunks(_psGetAllChunks.executeQuery());
        } catch (SQLException e) {
            DBUtil.close(_psGetAllChunks);
            _psGetAllChunks = null;
            throw e;
        }
    }

    private PreparedStatementWrapper _pswStartChunkUpload = new PreparedStatementWrapper();
    public void startChunkUpload_(ContentHash chunk, long length, Trans t) throws SQLException
    {
        if (l.isDebugEnabled()) l.debug("start chunk upload: " + chunk);
        assert S3ChunkAccessor.isOneChunk(chunk);
        PreparedStatementWrapper psw = _pswStartChunkUpload;
        PreparedStatement ps = psw.get();
        try {
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        _dbcw.insertOrIgnore() + " into " + T_ChunkCount + " (" +
                        C_ChunkCount_Hash + ',' + C_ChunkCount_Len + ',' +
                        C_ChunkCount_State + ',' + C_ChunkCount_Count +
                        ") VALUES(?,?,?,0)"));
            }

            ps.setBytes(1, chunk.getBytes());
            ps.setLong(2, length);
            ps.setInt(3, ChunkState.UPLOADING.sqlValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswFinishChunkUpload = new PreparedStatementWrapper();
    public void finishChunkUpload_(ContentHash chunk, Trans t) throws SQLException
    {
        if (l.isDebugEnabled()) l.debug("finish chunk upload: " + chunk);
        assert S3ChunkAccessor.isOneChunk(chunk);
        PreparedStatementWrapper psw = _pswFinishChunkUpload;
        PreparedStatement ps = psw.get();
        try {
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement("update " + T_ChunkCount +
                        " set " + C_ChunkCount_State + "=?" +
                        " where " + C_ChunkCount_Hash + "=?" +
                        " and " + C_ChunkCount_State + "=?"));
            }

            ps.setInt(1, ChunkState.UPLOADED.sqlValue());
            ps.setBytes(2, chunk.getBytes());
            ps.setInt(3, ChunkState.UPLOADING.sqlValue());
            ps.executeUpdate();
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswAdjustChunkCount = new PreparedStatementWrapper();
    private void adjustChunkCount_(ContentHash chunk, int delta, Trans t) throws SQLException
    {
        assert S3ChunkAccessor.isOneChunk(chunk);
        PreparedStatementWrapper psw = _pswAdjustChunkCount;
        PreparedStatement ps = psw.get();
        try {
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement("update " + T_ChunkCount +
                        " set " + C_ChunkCount_State + "=?, " +
                        C_ChunkCount_Count + "=" + C_ChunkCount_Count + "+?" +
                        " where " + C_ChunkCount_Hash + "=?"));
            }

            ps.setInt(1, ChunkState.REFERENCED.sqlValue());
            ps.setInt(2, delta);
            ps.setBytes(3, chunk.getBytes());
            int rows = ps.executeUpdate();
            assert rows == 1;
        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    public void incChunkCount_(ContentHash chunk, Trans t) throws SQLException
    {
        adjustChunkCount_(chunk, 1, t);
    }

    public void decChunkCount_(ContentHash chunk, Trans t) throws SQLException
    {
        adjustChunkCount_(chunk, -1, t);
    }

    private PreparedStatementWrapper _pswGetChunkState = new PreparedStatementWrapper();
    public ChunkState getChunkState_(ContentHash chunk) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetChunkState;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "select " + C_ChunkCount_State + " from " + T_ChunkCount +
                        " where " + C_ChunkCount_Hash + "=?"));
            }

            ps.setBytes(1, chunk.getBytes());
            ResultSet rs = ps.executeQuery();

            try {
                if (!rs.next()) return null;
                else return ChunkState.fromSql(rs.getInt(1));
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            psw.close();
            throw e;
        }
    }

    private PreparedStatementWrapper _pswGetChunkCount = new PreparedStatementWrapper();
    public long getChunkCount_(ContentHash chunk) throws SQLException
    {
        PreparedStatementWrapper psw = _pswGetChunkCount;
        try {
            PreparedStatement ps = psw.get();
            if (!isValid(ps)) {
                ps = psw.set(c().prepareStatement(
                        "select " + C_ChunkCount_Count + " from " + T_ChunkCount +
                        " where " + C_ChunkCount_Hash + "=?"));
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

    private PreparedStatement _psDeleteChunk;
    public void deleteChunk(ContentHash chunk, Trans t) throws SQLException
    {
        try {
            if (_psDeleteChunk == null) {
                _psDeleteChunk = c().prepareStatement(
                        "delete from " + T_ChunkCount + " where " + C_ChunkCount_Hash + "=?");
            }

            _psDeleteChunk.setBytes(1, chunk.getBytes());
            _psDeleteChunk.execute();
        } catch (SQLException e) {
            l.warn(Util.e(e));
            DBUtil.close(_psDeleteChunk);
            _psDeleteChunk=null;
            throw e;
        }
    }

    private static class DBIterDeadChunks extends AbstractDBIterator<ContentHash>
    {

        public DBIterDeadChunks(ResultSet rs) {
            super(rs);

        }

        @Override
        public ContentHash get_() throws SQLException {
            return new ContentHash(_rs.getBytes(1));
        }
    }

    private PreparedStatement _psGetDeadChunks;
    public IDBIterator<ContentHash> getDeadChunks() throws SQLException
    {
        try {
            if (_psGetDeadChunks == null) {
                _psGetDeadChunks = c().prepareStatement(
                        "select " + C_ChunkCount_Hash +
                        " from " + T_ChunkCount +
                        " where " + C_ChunkCount_Count + "=0");
            }

            return new DBIterDeadChunks(_psGetDeadChunks.executeQuery());

        } catch (SQLException e) {
            l.warn(Util.e(e));
            DBUtil.close(_psGetDeadChunks);
            _psGetDeadChunks = null;
            throw e;
        }
    }
}
