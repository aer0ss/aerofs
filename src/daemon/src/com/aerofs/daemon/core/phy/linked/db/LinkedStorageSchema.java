package com.aerofs.daemon.core.phy.linked.db;

import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.TableDumper;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.Statement;

public class LinkedStorageSchema implements ISchema
{
    public static final String
            // Non-representable objects
            T_NRO               = "nro",
            C_NRO_SIDX          = "nro_s",
            C_NRO_OID           = "nro_o",
            C_NRO_CONFLICT_OID  = "nro_c",      // non-null if representation conflict

            T_HIST_PATH         = "hp",
            C_HIST_PATH_ID      = "hp_i",
            C_HIST_PATH_PARENT  = "hp_p",
            C_HIST_PATH_NAME    = "hp_n",

            T_DELETED_FILE      = "df",
            C_DELETED_FILE_SIDX = "df_s",
            C_DELETED_FILE_OID  = "df_o",
            C_DELETED_FILE_PATH = "df_p",
            C_DELETED_FILE_REV  = "df_r",

            // Physical Staging Area
            T_PSA               = "psa",
            C_PSA_ID            = "psa_i",      // auto-inc unique id of entry
            C_PSA_PATH          = "psa_p",      // old path, if null files will not go to history
            C_PSA_REV           = "psa_r";

    @Override
    public void create_(Statement s, IDBCW dbcw) throws SQLException
    {
        createNROTable_(s, dbcw);
        createPhysicalStagingAreaTable_(s, dbcw);
        createHistoryTables_(s, dbcw);
    }

    public static void createHistoryTables_(Statement s, IDBCW dbcw) throws SQLException {
        s.executeUpdate("create table " + T_DELETED_FILE + "("
                + C_DELETED_FILE_SIDX + " integer not null,"
                + C_DELETED_FILE_OID + dbcw.uniqueIdType() + "not null,"
                + C_DELETED_FILE_PATH + " integer not null,"
                + C_DELETED_FILE_REV + " text not null,"
                + "primary key(" + C_DELETED_FILE_SIDX + "," + C_DELETED_FILE_OID + ")"
                + ")" + dbcw.charSet());
        s.executeUpdate("create table " + T_HIST_PATH + "("
                + C_HIST_PATH_ID + " integer primary key autoincrement,"
                + C_HIST_PATH_PARENT + " integer not null,"
                + C_HIST_PATH_NAME + " text not null,"
                + "unique(" + C_HIST_PATH_PARENT + "," + C_HIST_PATH_NAME + ")"
                + ")" + dbcw.charSet());
    }

    public static void createNROTable_(Statement s, IDBCW dbcw)
            throws SQLException
    {
        s.executeUpdate("create table " + T_NRO + "("
                + C_NRO_SIDX + " integer not null,"
                + C_NRO_OID + dbcw.uniqueIdType() + " not null,"
                + C_NRO_CONFLICT_OID + dbcw.uniqueIdType() + ","
                + "primary key (" + C_NRO_SIDX + "," + C_NRO_OID + ")"
                + ")" + dbcw.charSet());

        // used to list all CNROs associated with a given representable object
        // which allow a new "winner" to be efficiently selected whenever the representable
        // object is deleted/renamed
        s.executeUpdate(DBUtil.createIndex(T_NRO, 0, C_NRO_SIDX, C_NRO_CONFLICT_OID));
    }

    public static void createPhysicalStagingAreaTable_(Statement s, IDBCW dbcw)
            throws SQLException
    {
        s.executeUpdate("create table " + T_PSA + "("
                + C_PSA_ID + " integer primary key" + dbcw.autoIncrement() + ","
                + C_PSA_PATH + " text,"
                + C_PSA_REV + " text"
                + ")" + dbcw.charSet());
    }

    @Override
    public void dump_(Statement s, PrintStream ps) throws IOException, SQLException
    {
        TableDumper td = new TableDumper(new PrintWriter(ps));
        td.dumpTable(s, T_NRO);
    }
}
