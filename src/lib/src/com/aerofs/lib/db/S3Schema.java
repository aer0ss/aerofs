package com.aerofs.lib.db;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.dbcw.IDBCW;

public class S3Schema
{
    static final Logger l = Util.l(S3Schema.class);

    // mapping from internal name (SOKID) to file index
    public static final String
    T_FileInfo              = "s3fi",
    C_FileInfo_Index        = "s3fi_idx",
    C_FileInfo_Store        = "s3fi_sidx",
    C_FileInfo_InternalName = "s3fi_id";

    public static final String
    T_FileCurr              = "s3fc",
    C_FileCurr_Index        = "s3fc_idx",
    C_FileCurr_Store        = "s3fc_sidx",
    C_FileCurr_Ver          = "s3fc_ver",
    C_FileCurr_Parent       = "s3fc_parent",  // C_DirCurr_Index
    C_FileCurr_RealName     = "s3fc_name",
    C_FileCurr_Len          = "s3fc_len",
    C_FileCurr_Date         = "s3fc_date",
    C_FileCurr_Chunks       = "s3fc_chunks";

    public static final String
    T_FileHist              = "s3fh",
    C_FileHist_Index        = "s3fh_idx",
    C_FileHist_Store        = "s3fh_sidx",
    C_FileHist_Ver          = "s3fh_ver",
    C_FileHist_Parent       = "s3fh_parent",  // C_DirHist_Index
    C_FileHist_RealName     = "s3fh_name",
    C_FileHist_Len          = "s3fh_len",
    C_FileHist_Date         = "s3fh_date",
    C_FileHist_Chunks       = "s3fh_chunks";

    public static final String
    T_DirCurr               = "s3dc",
    C_DirCurr_Index         = "s3dc_idx",
    C_DirCurr_HistIndex     = "s3dc_hidx",  // C_DirHist_Index
    C_DirCurr_Parent        = "s3dc_parent",
    C_DirCurr_Name          = "s3dc_name";

    public static final String
    T_DirHist               = "s3dh",
    C_DirHist_Index         = "s3dh_idx",
    C_DirHist_Parent        = "s3dh_parent",
    C_DirHist_Name          = "s3dh_name";

    public static final String
    T_ChunkCount            = "s3co",
    C_ChunkCount_Hash       = "s3co_hash",
    C_ChunkCount_Len        = "s3co_len",
    C_ChunkCount_State      = "s3co_state",
    C_ChunkCount_Count      = "s3co_count";

    public static final String
    T_ChunkCache            = "s3ca",
    C_ChunkCache_Hash       = "s3ca_hash",
    C_ChunkCache_Time       = "s3ca_time";


    public enum ChunkState {
        UPLOADING(0), UPLOADED(1), REFERENCED(2);

        private static final ChunkState[] _fromSql;
        static {
            ChunkState[] values = values();
            _fromSql = new ChunkState[values.length];
            for (ChunkState cs : values) {
                _fromSql[cs.sqlValue()] = cs;
            }
        }

        public static ChunkState fromSql(int value)
        {
            assert value >= 0;
            if (value >= _fromSql.length) return null;
            return _fromSql[value];
        }

        private final int _sqlValue;

        private ChunkState(int sqlValue)
        {
            _sqlValue = sqlValue;
        }

        public int sqlValue()
        {
            return _sqlValue;
        }
    }


    private final IDBCW _dbcw;

    public S3Schema(IDBCW dbcw)
    {
        _dbcw = dbcw;
    }

    private void createIndex(Statement s, String table, int num, String... columns) throws SQLException
    {
        createIndex(s, false, table, num, columns);
    }

    private void createUniqueIndex(Statement s, String table, int num, String... columns) throws SQLException
    {
        createIndex(s, true, table, num, columns);
    }

    private void createIndex(Statement s, boolean unique, String table, int num, String... columns) throws SQLException
    {
        StringBuilder sb = new StringBuilder();
        sb.append("create ");
        if (unique) sb.append("unique ");
        sb.append("index " + table + num + " on " + table + "(");
        boolean first = true;
        for (String column : columns) {
            if (first) first = false;
            else sb.append(',');
            sb.append(column);
        }
        sb.append(')');
        String sql = sb.toString();
//        l.debug(sql);
        s.executeUpdate(sql);
    }

    public void create_() throws SQLException
    {
        Connection c = _dbcw.getConnection();
        Statement s = c.createStatement();

        try {
//            String chunkType = varBinaryType(HashAttrib.HASH_SIZE);
            String chunkType = " binary(" + ContentHash.UNIT_LENGTH + ") ";
            String chunkListType = " blob ";

            s.execute("create table if not exists " + T_FileInfo + "( " +
                    C_FileInfo_Index + _dbcw.longType() + " not null primary key " + _dbcw.autoIncrement() + ", " +
                    C_FileInfo_Store + _dbcw.longType() + " not null, " +
                    C_FileInfo_InternalName +  _dbcw.nameType() + " unique not null ) " +
                    _dbcw.charSet());
            createIndex(s, T_FileInfo, 0, C_FileInfo_Store, C_FileInfo_InternalName);

            s.execute("create table if not exists " + T_FileCurr + "( " +
                    C_FileCurr_Index + _dbcw.longType() + " not null primary key, " +
                    C_FileCurr_Store + _dbcw.longType() + " not null, " +
                    C_FileCurr_Ver + _dbcw.longType() + " not null, " +
                    C_FileCurr_Parent + _dbcw.longType() + " not null, "  +
                    C_FileCurr_RealName + _dbcw.nameType() + ", " +
                    C_FileCurr_Len + _dbcw.longType() + " not null, " +
                    C_FileCurr_Date + _dbcw.longType() + " not null, " +
                    C_FileCurr_Chunks + chunkListType + " not null ) " +
                    _dbcw.charSet());
            createUniqueIndex(s, T_FileCurr, 0, C_FileCurr_Parent, C_FileCurr_RealName);

            s.execute("create table if not exists " + T_FileHist + "( " +
                    C_FileHist_Index + _dbcw.longType() + " not null, " +
                    C_FileHist_Store + _dbcw.longType() + " not null, " +
                    C_FileHist_Ver + _dbcw.longType() + " not null, " +
                    C_FileHist_Parent + _dbcw.longType() + " not null, "  +
                    C_FileHist_RealName + _dbcw.nameType() + ", " +
                    C_FileHist_Len + _dbcw.longType() + " not null, " +
                    C_FileHist_Date + _dbcw.longType() + " not null, " +
                    C_FileHist_Chunks + chunkListType + " not null, " +
                    "primary key ( " + C_FileHist_Index + ',' + C_FileHist_Ver + ") ) " +
                    _dbcw.charSet());
            createIndex(s, T_FileHist, 0, C_FileHist_Parent, C_FileHist_RealName);

            s.execute("create table if not exists " + T_DirCurr + "( " +
                    C_DirCurr_Index + _dbcw.longType() + " not null primary key " + _dbcw.autoIncrement() + ", " +
                    C_DirCurr_HistIndex + _dbcw.longType() + " not null unique, " +
                    C_DirCurr_Parent + _dbcw.longType() + " not null, " +
                    C_DirCurr_Name + _dbcw.nameType() + " not null ) " +
                    _dbcw.charSet());
            createUniqueIndex(s, T_DirCurr, 0, C_DirCurr_Parent, C_DirCurr_Name);

            s.execute("create table if not exists " + T_DirHist + "( " +
                    C_DirHist_Index + _dbcw.longType() + " not null primary key " + _dbcw.autoIncrement() + ", " +
                    C_DirHist_Parent + _dbcw.longType() + " not null, " +
                    C_DirHist_Name + _dbcw.nameType() + " not null ) " +
                    _dbcw.charSet());
            createUniqueIndex(s, T_DirHist, 0, C_DirHist_Parent, C_DirHist_Name);

            s.execute("create table if not exists " + T_ChunkCount + "( " +
                    C_ChunkCount_Hash + chunkType + " not null primary key," +
                    C_ChunkCount_Len + _dbcw.longType() + " not null," +
                    C_ChunkCount_State + " integer not null," +
                    C_ChunkCount_Count + _dbcw.longType() + " not null ) " +
                    _dbcw.charSet());

            s.execute("create table if not exists " + T_ChunkCache + "( " +
                    C_ChunkCache_Hash + chunkType + " not null primary key," +
                    C_ChunkCache_Time + _dbcw.longType() + " not null )" +
                    _dbcw.charSet());
            createIndex(s, T_ChunkCache, 0, C_ChunkCache_Time);
        } finally {
            s.close();
        }

        c.commit();
    }

    public void dump_(PrintWriter pw) throws IOException, SQLException
    {
        Connection c = _dbcw.getConnection();
        Statement s = c.createStatement();
        try {
            TableDumper td = new TableDumper(pw);
            td.dumpTable(s, T_FileInfo);

            td.dumpTable(s, T_DirCurr);
            td.dumpTable(s, T_FileCurr);

            td.dumpTable(s, T_DirHist);
            td.dumpTable(s, T_FileHist);

            td.dumpTable(s, T_ChunkCount);
            td.dumpTable(s, T_ChunkCache);
        } finally {
            s.close();
        }
    }
}
