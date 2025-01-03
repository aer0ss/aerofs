package com.aerofs.daemon.core.polaris.db;

import com.aerofs.daemon.lib.db.ISchema;
import com.aerofs.lib.db.dbcw.IDBCW;

import java.sql.SQLException;
import java.sql.Statement;

public class PolarisSchema implements ISchema {
    public static String

            // Centralized object versions
            T_VERSION       = "cv",
            C_VERSION_SIDX  = "cv_s",
            C_VERSION_OID   = "cv_o",
            C_VERSION_TICK  = "cv_t",

            // local knowledge of central object tree
            T_REMOTE_LINK           = "rl",
            C_REMOTE_LINK_SIDX      = "rl_s",
            C_REMOTE_LINK_OID       = "rl_o",
            C_REMOTE_LINK_PARENT    = "rl_p",
            C_REMOTE_LINK_NAME      = "rl_n",
            C_REMOTE_LINK_VERSION   = "rl_v",

            // local knowledge of remote content versions (per-store)
            T_REMOTE_CONTENT            = "rc",
            C_REMOTE_CONTENT_OID        = "rc_o",
            C_REMOTE_CONTENT_VERSION    = "rc_v",
            C_REMOTE_CONTENT_DID        = "rc_d",
            C_REMOTE_CONTENT_HASH       = "rc_h",
            C_REMOTE_CONTENT_LENGTH     = "rc_l",

            // buffered meta changes
            T_META_BUFFER           = "mb",
            C_META_BUFFER_SIDX      = "mb_s",
            C_META_BUFFER_OID       = "mb_o",
            C_META_BUFFER_TYPE      = "mb_t",
            C_META_BUFFER_MIGRANT   = "mb_m",
            C_META_BUFFER_BOUND     = "mb_b",

            // Local meta changes   (per-store)
            T_META_CHANGE               = "mc",
            C_META_CHANGE_IDX           = "mc_i",
            C_META_CHANGE_OID           = "mc_o",
            C_META_CHANGE_MIGRANT       = "mc_m",
            C_META_CHANGE_NEW_PARENT    = "mc_p",
            C_META_CHANGE_NEW_NAME      = "mc_n",

            // local content changes (per-store)
            T_CONTENT_CHANGE        = "cc",
            C_CONTENT_CHANGE_IDX    = "cc_i",
            C_CONTENT_CHANGE_OID    = "cc_o",

            // remote content fetch queue (per-store)
            T_CONTENT_QUEUE         = "cq",
            C_CONTENT_QUEUE_IDX     = "cq_i",
            C_CONTENT_QUEUE_OID     = "cq_o",

            // content available post queue
            T_AVAILABLE_CONTENT         = "ac",
            C_AVAILABLE_CONTENT_SIDX    = "ac_s",
            C_AVAILABLE_CONTENT_OID     = "ac_o",
            C_AVAILABLE_CONTENT_VERSION = "ac_v";


    @Override
    public void create_(Statement s, IDBCW dbcw) throws SQLException {
        createPolarisFetchTables(s, dbcw);

        createAvailableContentTable(s, dbcw);
    }

    public static void createPolarisFetchTables(Statement s, IDBCW dbcw) throws SQLException
    {
        s.executeUpdate("create table " + T_VERSION + "("
                + C_VERSION_SIDX + " integer not null,"
                + C_VERSION_OID + dbcw.uniqueIdType() + "not null,"
                + C_VERSION_TICK + dbcw.longType() + "not null,"
                + "primary key(" + C_VERSION_SIDX + "," + C_VERSION_OID + ")"
                + ")");

        s.executeUpdate("create table " + T_REMOTE_LINK + "("
                + C_REMOTE_LINK_SIDX + " integer not null,"
                + C_REMOTE_LINK_OID + dbcw.uniqueIdType() + "not null,"
                + C_REMOTE_LINK_PARENT + dbcw.uniqueIdType() + "not null,"
                + C_REMOTE_LINK_NAME + dbcw.nameType() + "not null,"
                + C_REMOTE_LINK_VERSION + dbcw.longType() + "not null,"
                + "primary key("+ C_REMOTE_LINK_SIDX + "," + C_REMOTE_LINK_OID + "))");

        s.executeUpdate("create index " + T_REMOTE_LINK + "0 on " + T_REMOTE_LINK
                + "(" + C_REMOTE_LINK_SIDX + "," + C_REMOTE_LINK_PARENT + ")");

        s.executeUpdate("create table " + T_META_BUFFER + "("
                + C_META_BUFFER_SIDX + " integer not null,"
                + C_META_BUFFER_OID + dbcw.uniqueIdType() + "not null,"
                + C_META_BUFFER_TYPE + " integer not null,"
                + C_META_BUFFER_MIGRANT + dbcw.uniqueIdType() + ","
                + C_META_BUFFER_BOUND + dbcw.longType() + " not null,"
                + "primary key(" + C_META_BUFFER_SIDX + "," + C_META_BUFFER_OID + "))");

        s.executeUpdate("create index " + T_META_BUFFER + "0 on " + T_META_BUFFER
                + "(" + C_META_BUFFER_SIDX + "," + C_META_BUFFER_BOUND + ")");
    }

    public static void createAvailableContentTable(Statement s, IDBCW dbcw) throws SQLException {
        s.executeUpdate("create table " + T_AVAILABLE_CONTENT + "("
                + C_AVAILABLE_CONTENT_SIDX + " integer not null,"
                + C_AVAILABLE_CONTENT_OID + dbcw.uniqueIdType() + "not null,"
                + C_AVAILABLE_CONTENT_VERSION + dbcw.longType() + "not null,"
                + "primary key(" + C_AVAILABLE_CONTENT_SIDX + ", " + C_AVAILABLE_CONTENT_OID + "))");
    }
}
