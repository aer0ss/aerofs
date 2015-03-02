/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.db.TableDumper;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.lib.db.DBUtil.createIndex;
import static com.aerofs.lib.db.DBUtil.createUniqueIndex;

public class BlockStorageSchema implements ISchema
{
    // mapping between internal name (SOKID) and file index (primary key, auto-increment)
    public static final String
            T_FileInfo              = "bsfi",
            C_FileInfo_Index        = "bsfi_idx",
            C_FileInfo_InternalName = "bsfi_id";

    // File info (current)
    public static final String
            T_FileCurr              = "bsfc",
            C_FileCurr_Index        = "bsfc_idx",
            C_FileCurr_Ver          = "bsfc_ver",
            C_FileCurr_Len          = "bsfc_len",
            C_FileCurr_Date         = "bsfc_date",
            C_FileCurr_Chunks       = "bsfc_chunks";

    // File info (history)
    public static final String
            T_FileHist              = "bsfh",
            C_FileHist_Index        = "bsfh_idx",
            C_FileHist_Ver          = "bsfh_ver",
            C_FileHist_Parent       = "bsfh_parent",  // C_DirHist_Index
            C_FileHist_RealName     = "bsfh_name",
            C_FileHist_Len          = "bsfh_len",
            C_FileHist_Date         = "bsfh_date",
            C_FileHist_Chunks       = "bsfh_chunks";

    // Directory History (IPhysicalRevProvider interface forces us to maintain full path mapping)
    public static final String
            T_DirHist               = "bsdh",
            C_DirHist_Index         = "bsdh_idx",
            C_DirHist_Parent        = "bsdh_parent",
            C_DirHist_Name          = "bsdh_name";

    // Block stats (ref count, length, hash, ...)
    public static final String
            T_BlockCount            = "bsco",
            C_BlockCount_Hash       = "bsco_hash", // content hash *before* backend encoding
            C_BlockCount_Len        = "bsco_len",  // content length *before* backend encoding
            C_BlockCount_State      = "bsco_state",
            C_BlockCount_Count      = "bsco_count";

    public enum BlockState
    {
        STORING(0), STORED(1), REFERENCED(2);

        private static final BlockState[] _fromSql;
        static {
            BlockState[] values = values();
            _fromSql = new BlockState[values.length];
            for (BlockState cs : values) {
                _fromSql[cs.sqlValue()] = cs;
            }
        }

        public static BlockState fromSql(int value)
        {
            assert value >= 0;
            if (value >= _fromSql.length) return null;
            return _fromSql[value];
        }

        private final int _sqlValue;

        private BlockState(int sqlValue)
        {
            _sqlValue = sqlValue;
        }

        public int sqlValue()
        {
            return _sqlValue;
        }
    }

    @Override
    public void create_(Statement s, IDBCW dbcw) throws SQLException
    {
        String chunkType = " binary(" + ContentBlockHash.UNIT_LENGTH + ") ";
        String chunkListType = " blob ";

        s.execute("create table " + T_FileInfo + "( " +
                C_FileInfo_Index + dbcw.longType() + " not null primary key " +
                dbcw.autoIncrement() + ", " +
                C_FileInfo_InternalName +  dbcw.nameType() + " unique not null ) " +
                dbcw.charSet());
        s.executeUpdate(createIndex(T_FileInfo, 0, C_FileInfo_InternalName));

        s.execute("create table " + T_FileCurr + "( " +
                C_FileCurr_Index + dbcw.longType() + " not null primary key, " +
                C_FileCurr_Ver + dbcw.longType() + " not null, " +
                C_FileCurr_Len + dbcw.longType() + " not null, " +
                C_FileCurr_Date + dbcw.longType() + " not null, " +
                C_FileCurr_Chunks + chunkListType + " not null ) " +
                dbcw.charSet());

        s.execute("create table " + T_FileHist + "( " +
                C_FileHist_Index + dbcw.longType() + " not null, " +
                C_FileHist_Ver + dbcw.longType() + " not null, " +
                C_FileHist_Parent + dbcw.longType() + " not null, "  +
                C_FileHist_RealName + dbcw.nameType() + ", " +
                C_FileHist_Len + dbcw.longType() + " not null, " +
                C_FileHist_Date + dbcw.longType() + " not null, " +
                C_FileHist_Chunks + chunkListType + " not null, " +
                "primary key ( " + C_FileHist_Index + ',' + C_FileHist_Ver + ") ) " +
                dbcw.charSet());
        s.executeUpdate(createIndex(T_FileHist, 0, C_FileHist_Parent, C_FileHist_RealName));

        s.execute("create table " + T_DirHist + "( " +
                C_DirHist_Index + dbcw.longType() + " not null primary key " +
                dbcw.autoIncrement() + ", " +
                C_DirHist_Parent + dbcw.longType() + " not null, " +
                C_DirHist_Name + dbcw.nameType() + " not null ) " +
                dbcw.charSet());
        s.executeUpdate(createUniqueIndex(T_DirHist, 0, C_DirHist_Parent, C_DirHist_Name));

        s.execute("create table " + T_BlockCount + "( " +
                C_BlockCount_Hash + chunkType + " not null primary key," +
                C_BlockCount_Len + dbcw.longType() + " not null," +
                C_BlockCount_State + " integer not null," +
                C_BlockCount_Count + dbcw.longType() + " not null ) " +
                dbcw.charSet());
    }

    @Override
    public void dump_(Statement s, PrintStream ps) throws IOException, SQLException
    {
        TableDumper td = new TableDumper(new PrintWriter(ps));
        td.dumpTable(s, T_FileInfo);

        td.dumpTable(s, T_FileCurr);

        td.dumpTable(s, T_DirHist);
        td.dumpTable(s, T_FileHist);

        td.dumpTable(s, T_BlockCount);
    }
}
