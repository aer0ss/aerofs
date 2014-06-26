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

            // Physical Staging Area
            T_PSA               = "psa",
            C_PSA_ID            = "psa_i",      // auto-inc unique id of entry
            C_PSA_PATH          = "psa_p";      // old path, if null files will not go to history

    @Override
    public void create_(Statement s, IDBCW dbcw) throws SQLException
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

        createPhysicalStagingAreaTable_(s, dbcw);
    }

    public static void createPhysicalStagingAreaTable_(Statement s, IDBCW dbcw)
            throws SQLException
    {
        s.executeUpdate("create table " + T_PSA + "("
                + C_PSA_ID + " integer primary key" + dbcw.autoIncrement() + ","
                + C_PSA_PATH + " text"
                + ")" + dbcw.charSet());
    }

    @Override
    public void dump_(Statement s, PrintStream ps) throws IOException, SQLException
    {
        TableDumper td = new TableDumper(new PrintWriter(ps));
        td.dumpTable(s, T_NRO);
    }
}
