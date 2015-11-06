/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.lib.LibParam;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.google.inject.Inject;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.sql.Statement;

public class SyncSchema implements ISchema
{
    public static final String

            // Stores
            T_STORE         = "s",
            C_STORE_SIDX    = "s_i",        // SIndex
            C_STORE_NAME    = "s_n",        // String
            C_STORE_COLLECTING_CONTENT = "s_c", // boolean
            C_STORE_LTS_LOCAL   = "s_lts",      // long: polaris logical timestamp
            C_STORE_LTS_CONTENT = "s_rts",      // long: polaris logical timestamp
            // This deprecated field is still in old databases. See DPUTClearSyncStatusColumns
            // C_STORE_DIDS    = "s_d",     // concatenated DIDs
            C_STORE_USAGE   = "s_u",        // sum of content size, in bytes

            // Store Hierarchy. See IStores' class-level comments for details.
            // parents.
            T_SH             = "sh",
            C_SH_SIDX        = "sh_s",      // SIndex
            C_SH_PARENT_SIDX = "sh_p",      // SIndex

            // SIndex-SID map. It maintains bidirectional map between SIndexes and SIDs
            T_SID           = "i",
            C_SID_SIDX      = "i_i",        // SIndex
            C_SID_SID       = "i_d",        // SID

            // Prefixes
            T_PRE           = "r",          // prefix table
            C_PRE_SIDX      = "r_s",        // SIndex
            C_PRE_OID       = "r_o",        // OID
            // KIndex, INVALID if not local (null doesn't work well with "replace" statement when
            // adding a KML version)
            C_PRE_KIDX      = "r_k",
            C_PRE_DID       = "r_d",        // DID
            C_PRE_TICK      = "r_t",        // Tick

            // ACL Table
            T_ACL            = "a",
            C_ACL_SIDX       = "a_i",       // SIndex
            C_ACL_SUBJECT    = "a_b",       // String
            C_ACL_ROLE       = "a_r",       // Role

            // Epoch Table (ACL, SyncStatus, ...) (one row only)
            T_EPOCH             = "ep",
            C_EPOCH_ACL         = "ep_ep",     // acl epoch
            C_EPOCH_AUDIT_PUSH  = "ep_dp",     // index of last activity log entry pushed to the auditor
            // This deprecated fields are still in old databases. See DPUTClearSyncStatusColumns
            // C_EPOCH_SYNC_PULL   = "ep_s",   // sync status pull epoch (server-issued)
            // C_EPOCH_SYNC_PUSH   = "ep_a",   // index of last local activity pushed to server

            // Collector Filter
            T_CF             = "cf",
            C_CF_SIDX        = "cf_s",      // SIndex
            C_CF_DID         = "cf_d",      // DID
            C_CF_FILTER      = "cf_f",      // BFOID

            // Sender Filter
            T_SF             = "sf",
            C_SF_SIDX        = "sf_s",      // SIndex
            C_SF_SFIDX       = "sf_i",      // SenderFilterIndex
            C_SF_FILTER      = "sf_f",      // BFOID

            // Sender Device
            T_SD             = "sd",
            C_SD_SIDX        = "sd_s",      // SIndex
            C_SD_DID         = "sd_d",      // DID
            C_SD_SFIDX       = "sd_i",      // SenderFilterIndex

            // Pulled Devices Table
            T_PD             = "pd",
            C_PD_SIDX        = "pd_s",      // SIndex
            C_PD_DID         = "pd_d",      // DID

            // DID-to-User mapping
            T_D2U            = "d",
            C_D2U_DID        = "d_d",       // DID
            C_D2U_USER       = "d_u",       // String

            // Device Name Table
            T_DN             = "dn",
            C_DN_DID         = "dn_d",      // DID
            C_DN_NAME        = "dn_n",      // String. null if not provided by SP
            C_DN_TIME        = "dn_t",      // long. the time when the entry is added

            // User Name Table
            T_UN             = "un",
            C_UN_USER        = "un_u",      // String
            C_UN_FIRST_NAME  = "un_f",      // String. null if not provided by SP
            C_UN_LAST_NAME   = "un_l",      // String. null if not provided by SP
            C_UN_TIME        = "un_t",      // long. the time when the entry is added

            // Logical Staging Area
            T_LSA               = "lsa",
            C_LSA_SIDX          = "lsa_s",
            C_LSA_OID           = "lsa_o",
            C_LSA_HISTORY_PATH  = "lsa_p",  // path for sync history, empty if no history is kept
            C_LSA_REV           = "lsa_r";

    @Inject
    public SyncSchema()
    {
    }

    @Override
    public void create_(Statement s, IDBCW dbcw) throws SQLException
    {
        // TODO (AAG) all updates have to detect MySQL and use engine=InnoDB
        assert !dbcw.isMySQL();

        s.executeUpdate(
                "create table " + T_SID + "(" +
                    C_SID_SIDX + " integer primary key " + dbcw.autoIncrement() +"," +
                    C_SID_SID + dbcw.uniqueIdType() + " unique not null" +
                ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_PRE + "(" +
                    C_PRE_SIDX + " integer not null," +
                    C_PRE_OID + dbcw.uniqueIdType() + " not null," +
                    C_PRE_KIDX + " integer not null," +
                    C_PRE_DID + dbcw.uniqueIdType() + " not null," +
                    C_PRE_TICK + dbcw.longType() + " not null," +
                    "primary key (" + C_PRE_SIDX + "," + C_PRE_OID + "," +
                        C_PRE_KIDX + "," + C_PRE_DID + ")" +
                ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_ACL + " (" +
                C_ACL_SIDX + " integer not null, " +
                C_ACL_SUBJECT + dbcw.userIdType() + " not null, " +
                C_ACL_ROLE + dbcw.roleType() + " not null, " +
                "primary key (" + C_ACL_SIDX + "," + C_ACL_SUBJECT + ")" +
                ")" + dbcw.charSet());

        // NOTE: although auditing is disabled in
        // hybrid installations, it's OK to create
        // an additional column for it.
        // this simplifies the code here and imposes
        // no cost. Moreover, this allows us to
        // use the identical db schema across installation
        // types
        s.executeUpdate(
                "create table " + T_EPOCH + " (" +
                        C_EPOCH_ACL        + dbcw.longType() + " not null," +
                        C_EPOCH_AUDIT_PUSH + dbcw.longType() + " not null" +
                        ")" + dbcw.charSet());
        s.executeUpdate(
                "insert into " + T_EPOCH +
                        " (" +
                            C_EPOCH_ACL + "," +
                            C_EPOCH_AUDIT_PUSH +
                        ")" +
                        " values("+
                            LibParam.INITIAL_ACL_EPOCH + "," +
                            LibParam.INITIAL_AUDIT_PUSH_EPOCH +
                        ")");

        s.executeUpdate(
                "create table " + T_STORE + "(" +
                        C_STORE_SIDX + " integer primary key," +
                        C_STORE_NAME + dbcw.nameType() + "," +
                        C_STORE_COLLECTING_CONTENT + dbcw.boolType() + " not null," +
                        C_STORE_USAGE + " integer not null default 0" +
                        ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_SH + "(" +
                        C_SH_SIDX + " integer not null," +
                        C_SH_PARENT_SIDX + " integer not null," +
                        "unique (" + C_SH_SIDX + "," + C_SH_PARENT_SIDX + ")" +
                        ")" + dbcw.charSet());

        // for getParents()
        s.executeUpdate(
                "create index " + T_SH + "0 on " + T_SH + "(" + C_SH_SIDX + ")");

        // for getChildren()
        s.executeUpdate("create index " + T_SH + "1 on " + T_SH + "(" + C_SH_PARENT_SIDX + ")");


        s.executeUpdate(
                "create table if not exists " + T_D2U + "(" +
                        C_D2U_DID + dbcw.uniqueIdType() + " primary key," +
                        C_D2U_USER + dbcw.userIdType() + " not null" +
                        ")" + dbcw.charSet());

        s.executeUpdate(
                "create table if not exists " + T_UN + "(" +
                        C_UN_USER + dbcw.userIdType() + " primary key," +
                        C_UN_FIRST_NAME + " text," +
                        C_UN_LAST_NAME + " text," +
                        C_UN_TIME + dbcw.longType() + " not null" +
                        ")" + dbcw.charSet());

        s.executeUpdate(
                "create table if not exists " + T_DN + "(" +
                        C_DN_DID + dbcw.uniqueIdType() + " primary key," +
                        C_DN_NAME + " text," +
                        C_DN_TIME + dbcw.longType() + " not null" +
                        ")" + dbcw.charSet());

        createCollectorTables_(s, dbcw);
        createLogicalStagingArea(s, dbcw);
    }

    public static void createCollectorTables_(Statement s, IDBCW dbcw) throws SQLException {
        s.executeUpdate(
                "create table " + T_CF + " (" +
                        C_CF_SIDX + " integer not null," +
                        C_CF_DID + dbcw.uniqueIdType() + "not null," +
                        C_CF_FILTER + dbcw.bloomFilterType() + " not null," +
                        "primary key (" + C_CF_SIDX + "," + C_CF_DID + ")" +
                        ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_PD + " (" +
                        C_PD_SIDX + " integer not null, " +
                        C_PD_DID + dbcw.uniqueIdType() + " not null, " +
                        "primary key (" + C_PD_SIDX + "," + C_PD_DID + ")" +
                        ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_SF + " (" +
                        C_SF_SIDX + " integer not null," +
                        C_SF_SFIDX + dbcw.longType() + "not null," +
                        C_SF_FILTER + dbcw.bloomFilterType() + " not null," +
                        "primary key (" + C_SF_SIDX + "," + C_SF_SFIDX + ")" +
                        ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_SD + " (" +
                        C_SD_SIDX + " integer not null," +
                        C_SD_DID + dbcw.uniqueIdType() + " not null," +
                        C_SD_SFIDX + dbcw.longType() + " not null," +
                        "primary key (" + C_SD_SIDX + "," + C_SD_DID + ")" +
                        ")" + dbcw.charSet());

        // for getSenderDeviceIndexCount()
        s.executeUpdate(
                "create index " + T_SD + "0 on "
                        + T_SD +
                        "(" + C_SD_SIDX + "," + C_SD_SFIDX + ")");
    }

    public static void createLogicalStagingArea(Statement s, IDBCW dbcw) throws SQLException
    {
        s.executeUpdate("create table " + T_LSA + "("
                + C_LSA_SIDX + " integer not null,"
                + C_LSA_OID + dbcw.uniqueIdType() + "not null,"
                + C_LSA_HISTORY_PATH + " text not null,"
                + C_LSA_REV + " text,"
                + "primary key(" + C_LSA_SIDX + "," + C_LSA_OID + ")"
                + ")");
    }

    @Override
    public void dump_(Statement s, PrintStream ps) throws IOException, SQLException
    {
        // TODO:
    }
}
