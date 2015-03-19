/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.injectable.InjectableDriver;
import com.google.inject.Inject;

public class CoreSchema extends SyncSchema
{
    public static final String
            // SIndex->{DID} map: for each store, maintain a set of DID having contributed ticks
            T_SC            = "sc",
            C_SC_SIDX       = "sc_s",
            C_SC_DID        = "sc_d",


            // Distributed Versions
            T_VER           = "v",
            C_VER_SIDX      = "v_i",        // SIndex
            C_VER_OID       = "v_o",        // OID
            C_VER_CID       = "v_c",        // CID
            // KIndex, INVALID if not local (null doesn't work well with "replace" statement when
            // adding a KML version)
            C_VER_KIDX      = "v_k",
            C_VER_DID       = "v_d",        // DID
            C_VER_TICK      = "v_t",        // Tick

            // Knowledge
            T_KWLG          = "k",
            C_KWLG_SIDX     = "k_s",        // SIndex
            C_KWLG_DID      = "k_d",        // DID
            C_KWLG_TICK     = "k_t",        // Tick

            // Immigrant Versions
            T_IV            = "iv",
            C_IV_SIDX       = "iv_s",       // SIndex
            C_IV_IMM_DID    = "iv_id",      // immigrant DID
            C_IV_IMM_TICK   = "iv_it",      // immigrant Tick
            C_IV_OID        = "iv_o",       // OID
            C_IV_CID        = "iv_c",       // CID
            C_IV_DID        = "iv_d",       // DID
            C_IV_TICK       = "iv_t",       // Tick

            // Immigrant Knowledge
            T_IK            = "ik",
            C_IK_SIDX       = "ik_s",       // SIndex
            C_IK_IMM_DID    = "ik_d",       // DID
            C_IK_IMM_TICK   = "ik_t",       // Tick

            // Object Attributes
            T_OA          = "o",
            C_OA_SIDX     = "o_s",          // SIndex
            C_OA_OID      = "o_o",          // OID
            // global attributes. part of metadata
            C_OA_NAME     = "o_n",          // String
            // OID. the parent of the root folder is the SID of the parent store. this is to
            // facilitate OID-to-path lookups
            C_OA_PARENT   = "o_p",
            C_OA_TYPE     = "o_d",          // Type
            C_OA_FLAGS    = "o_l",          // int
            // local attributes. never transmitted through wire
            C_OA_FID      = "o_f",          // blob. file id (i.e. i-node number)

            // Content Attributes
            T_CA            = "c",
            C_CA_SIDX       = "c_s",        // SIndex
            C_CA_OID        = "c_o",        // OID
            C_CA_KIDX       = "c_k",        // KIndex
            C_CA_LENGTH     = "c_l",        // long
            C_CA_HASH       = "c_h",        // SHA-256
            C_CA_MTIME      = "c_t",        // mtime in ms, long
            // This deprecated fields are still in old databases. See DPUTClearSyncStatusColumns
            // C_OA_SYNC     = "o_st",      // blob. syncstatus bitvector
            // C_OA_AG_SYNC  = "o_as",      // blob. aggregate sync status (folder only)

            // Alias Table
            T_ALIAS              = "al",
            C_ALIAS_SIDX         = "al_i",  // SIndex
            C_ALIAS_SOURCE_OID   = "al_s",  // Aliased oid
            C_ALIAS_TARGET_OID   = "al_t",  // Target

            // Max ticks
            T_MAXTICK          = "t",
            C_MAXTICK_SIDX     = "t_s",     // SIndex
            C_MAXTICK_OID      = "t_o",
            C_MAXTICK_CID      = "t_c",     // CID
            C_MAXTICK_DID      = "t_d",     // DID
            C_MAXTICK_MAX_TICK = "t_m",     // Tick

            // Collector Filter
            T_CF             = "cf",
            C_CF_SIDX        = "cf_s",      // SIndex
            C_CF_DID         = "cf_d",      // DID
            C_CF_FILTER      = "cf_f",      // BFOID

            // Collector Sequence
            T_CS             = "cs",
            C_CS_CS          = "cs_cs",     // long
            C_CS_SIDX        = "cs_s",      // SIndex
            C_CS_OID         = "cs_o",      // OID
            C_CS_CID         = "cs_c",      // CID

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

            // Greatest Ticks. this table has only one row. it's designed to maintain
            // the greatest ticks that the local peers has ever used
            T_GT             = "gt",
            C_GT_NATIVE      = "gt_n",      // Tick
            C_GT_IMMIGRANT   = "gt_i",      // Tick

            // Pulled Devices Table
            T_PD             = "pd",
            C_PD_SIDX        = "pd_s",      // SIndex
            C_PD_DID         = "pd_d",      // DID

            // Expulsion Table
            T_EX             = "e",
            C_EX_SIDX        = "e_s",       // SIndex
            C_EX_OID         = "e_o",       // OID

            // Logical Staging Area
            T_LSA               = "lsa",
            C_LSA_SIDX          = "lsa_s",
            C_LSA_OID           = "lsa_o",
            C_LSA_HISTORY_PATH  = "lsa_p",  // path for sync history, empty if no history is kept

            // Activity Log Table. See IActivityLogDatabase.ActivityRow for field details.
            T_AL             = "ao",
            C_AL_IDX         = "ao_i",      // long autoincrement primary key
            C_AL_SIDX        = "ao_s",      // SIndex
            C_AL_OID         = "ao_o",      // OID
            C_AL_TYPE        = "ao_t",      // int
            C_AL_PATH        = "ao_p",      // String
            C_AL_PATH_TO     = "ao_pt",     // String
            C_AL_DIDS        = "ao_d",      // bytes: one or more concatenated DIDs
            C_AL_TIME        = "ao_ts",     // long

            // SP Leave (Shared Folder) Queue
            T_SPQ           = "spq",
            C_SPQ_IDX       = "spq_i",
            C_SPQ_SID       = "spq_s",
            C_SPQ_TYPE      = "spq_t",
            C_SPQ_NAME      = "spq_n",

            // unlinked external folders
            T_UNLINKED_ROOT = "pr",
            C_UNLINKED_ROOT_SID = "pr_s",
            C_UNLINKED_ROOT_NAME = "pr_n";


    private final InjectableDriver _dr;

    @Inject
    public CoreSchema(InjectableDriver dr)
    {
        _dr = dr;
    }

    @Override
    public void create_(Statement s, IDBCW dbcw) throws SQLException
    {
        super.create_(s, dbcw);

        // NB. adding "unique (C_VER_DID, C_VER_TICK)" may change the behavior
        // of "replace" statements
        s.executeUpdate(
                "create table " + T_VER + "(" +
                    C_VER_SIDX + " integer not null," +
                    C_VER_OID + dbcw.uniqueIdType() + " not null,"+
                    C_VER_CID + " integer not null," +
                    C_VER_KIDX + " integer not null," +
                    C_VER_DID + dbcw.uniqueIdType() + "not null, "+
                    C_VER_TICK + dbcw.longType() + " not null," +
                    "primary key (" + C_VER_SIDX + "," + C_VER_OID + "," +
                        C_VER_CID + "," + C_VER_DID + "," + C_VER_KIDX + ")" +
                ")" + dbcw.charSet());

        /*
         * used by:
         * 1. updateMaxTicks_
         *
         * NOTE: Although this is extremely similar to the primary key above,
         * including C_VER_TICK allows us to avoid a table scan.
         *
         * NOTE: the usefulness of this index is NOT tested on mysql, and is
         * removed from the sp daemon database for now.
         */
        s.executeUpdate(
                "create index " + T_VER + "0 on " + T_VER +
                    "(" + C_VER_SIDX + "," + C_VER_OID + "," + C_VER_CID +
                          "," +  C_VER_DID + "," + C_VER_TICK + ")"
                    );

        s.executeUpdate(
                "create table " + T_KWLG + "(" +
                    C_KWLG_SIDX + " integer not null," +
                    C_KWLG_DID + dbcw.uniqueIdType() + " not null," +
                    C_KWLG_TICK + dbcw.longType() + " not null," +
                    "primary key (" + C_KWLG_SIDX + "," + C_KWLG_DID + ")" +
                ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_IV + "(" +
                    C_IV_SIDX + " integer not null," +
                    C_IV_OID + dbcw.uniqueIdType() + " not null," +
                    C_IV_CID + " integer not null," +
                    C_IV_DID + dbcw.uniqueIdType() + "not null, " +
                    C_IV_TICK + dbcw.longType() + " not null," +
                    C_IV_IMM_DID + dbcw.uniqueIdType() + " not null, " +
                    C_IV_IMM_TICK + dbcw.longType() + " not null," +
                    "primary key (" + C_IV_SIDX + "," + C_IV_OID + "," +
                        C_IV_CID + "," + C_IV_DID + ")" +
                ")" + dbcw.charSet());

        /* used by:
         * 1. getImmigrantTicks_()
         * 2. getAllImmigrantVersionDIDs_()
         */
        s.executeUpdate(
                "create unique index " + T_IV + "0 on " + T_IV +
                    "(" + C_IV_SIDX + "," + C_IV_IMM_DID + "," + C_IV_IMM_TICK +
                ")");

        s.executeUpdate(
                "create table " + T_IK + "(" +
                    C_IK_SIDX + " integer not null," +
                    C_IK_IMM_DID + dbcw.uniqueIdType() + " not null,"+
                    C_IK_IMM_TICK + dbcw.longType() + " not null," +
                    "primary key (" + C_IK_SIDX + "," + C_IK_IMM_DID + ")" +
                ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_OA + "(" +
                        C_OA_SIDX + " integer not null," +
                        C_OA_OID + dbcw.uniqueIdType() + " not null," +
                        C_OA_NAME + dbcw.nameType() + " not null," +
                        C_OA_PARENT + dbcw.uniqueIdType() + " not null," +
                        C_OA_TYPE + " integer not null, " +
                        C_OA_FID + dbcw.fidType(_dr.getFIDLength()) + "," +
                        C_OA_FLAGS + " integer not null," +
                        "primary key (" + C_OA_SIDX + "," + C_OA_OID + ")" +
                        ")" + dbcw.charSet());

        // for name conflict detection and getChild()
        s.executeUpdate(
                "create unique index " + T_OA + "0 on " + T_OA +
                        "(" + C_OA_SIDX + "," + C_OA_PARENT + "," + C_OA_NAME + ")");

        // for getSOID_(FID), and for uniqueness constraint on FIDs
        s.executeUpdate(
                "create unique index " + T_OA + "1 on " + T_OA + "(" + C_OA_FID + ")");

        s.executeUpdate(
                "create table " + T_CA + "(" +
                    C_CA_SIDX + " integer not null," +
                    C_CA_OID + dbcw.uniqueIdType() + " not null," +
                    C_CA_KIDX + " integer not null," +
                    C_CA_LENGTH + dbcw.longType() + " not null," +
                    C_CA_HASH +  " blob,  " +
                    C_CA_MTIME + dbcw.longType() + ", " +
                    "primary key (" + C_CA_SIDX + "," + C_CA_OID + "," +
                    C_CA_KIDX + ")" +
                ")" + dbcw.charSet());

        // for getAllNonMasterBranches_()
        s.executeUpdate("create index if not exists "
                + T_CA + "0 on " + T_CA + "(" + C_CA_KIDX + ")");

        /*
         * The mt (maxticks) table was created to solve performance problems with the GetVers query.
         * GetVers calls Database::getTicks_ which, given the tuple (sid, did) returned a set of
         * (oid, cid, max_tick) tuples for that (sid, did). This query involved an aggregate function (max),
         * and multiple orderings (group by, order).
         *
         * To avoid performing this query on every call we create a side table called mt that hold the
         * latest known max_tick for the tuple (sid, oid, cid, did). This side table is updated on each
         * version increment or store deletion, and allows the GetVers message to be populated without using
         * aggregate functions.
         *
         * There are two indexes on this table:
         * 1. (sid, oid, cid, did): used on all insertions
         * 2. (sid, did, max_tick): used by Database::getTicks
         */
        s.executeUpdate(
                "create table " + T_MAXTICK + "(" +
                    C_MAXTICK_SIDX + " integer not null, " +
                    C_MAXTICK_OID + dbcw.uniqueIdType() + " not null, " +
                    C_MAXTICK_CID + " integer not null, " +
                    C_MAXTICK_DID + dbcw.uniqueIdType() + " not null, " +
                    C_MAXTICK_MAX_TICK + dbcw.longType() + " not null, " +
                    "primary key (" + C_MAXTICK_SIDX + ", " + C_MAXTICK_OID +
                    ", " + C_MAXTICK_CID + ", " + C_MAXTICK_DID + ")" +
                    ")" + dbcw.charSet());

        // for getTicks_()
        s.executeUpdate(
                "create index " + T_MAXTICK + "0 on "
                    + T_MAXTICK +
                    "(" + C_MAXTICK_SIDX + "," + C_MAXTICK_DID + ", " +
                    C_MAXTICK_MAX_TICK + ")");

        s.executeUpdate(
                "create table " + T_CS + "(" +
                C_CS_CS + dbcw.longType() + " primary key " + dbcw.autoIncrement() + "," +
                C_CS_SIDX + " integer not null," +
                C_CS_OID + dbcw.uniqueIdType() + "not null," +
                C_CS_CID + " integer not null," +
                "unique (" + C_CS_SIDX + "," + C_CS_OID + "," + C_CS_CID + ")" +
                ")" + dbcw.charSet());

        // for getAllCS_()
        s.executeUpdate(
                // (sidx,cs_cs,oid,cid) is required as an index, so the queries of
                // "sidx=#, order by cs_cs" in the callers of getFromCSImpl_()
                // do not require a temporary b-tree
                "create index " + T_CS + "0 on "
                        + T_CS + "(" + C_CS_SIDX + "," + C_CS_CS + "," + C_CS_OID + ","
                        + C_CS_CID + ")");

        s.executeUpdate(
                "create table " + T_CF + " (" +
                C_CF_SIDX + " integer not null," +
                C_CF_DID + dbcw.uniqueIdType() + "not null," +
                C_CF_FILTER + dbcw.bloomFilterType() + " not null," +
                "primary key (" + C_CF_SIDX + "," + C_CF_DID + ")" +
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

        s.executeUpdate(
                "create table " + T_ALIAS + " (" +
                 C_ALIAS_SIDX + " integer not null," +
                 C_ALIAS_SOURCE_OID + dbcw.uniqueIdType() + " not null, " +
                 C_ALIAS_TARGET_OID + dbcw.uniqueIdType() + " not null, " +
                 "primary key (" + C_ALIAS_SIDX + "," + C_ALIAS_SOURCE_OID + ")" +
                 ")" + dbcw.charSet());

        // for resolveAliasChaining_()
        s.executeUpdate(
                "create index " + T_ALIAS + "0 on " + T_ALIAS +
                    "(" + C_ALIAS_SIDX + "," + C_ALIAS_TARGET_OID + ")");

        s.executeUpdate(
                "create table " + T_GT + " (" +
                C_GT_NATIVE + dbcw.longType() + "," +
                C_GT_IMMIGRANT + dbcw.longType() + ")" + dbcw.charSet());
        s.executeUpdate(
                "insert into " + T_GT + " (" + C_GT_NATIVE + "," +
                        C_GT_IMMIGRANT + ") values (0,0)");

        s.executeUpdate(
                "create table " + T_PD + " (" +
                C_PD_SIDX + " integer not null, " +
                C_PD_DID + dbcw.uniqueIdType() + " not null, " +
                "primary key (" + C_PD_SIDX + "," + C_PD_DID + ")" +
                ")" + dbcw.charSet());

        s.executeUpdate(
                "create table " + T_EX + " (" +
                C_EX_SIDX + " integer not null, " +
                C_EX_OID + dbcw.uniqueIdType() + " not null, " +
                "primary key (" + C_EX_SIDX + "," + C_EX_OID + ")" +
                ")" + dbcw.charSet());

        // "if not exists" is needs since the method is used by a DPUT, which requires this method
        // to be idempotent.
        s.executeUpdate("create table if not exists " + T_AL + "(" +
                C_AL_IDX + dbcw.longType() + " primary key " + dbcw.autoIncrement() + "," +
                C_AL_SIDX + " integer not null," +
                C_AL_OID + dbcw.uniqueIdType() + "not null," +
                C_AL_TYPE + " integer not null," +
                C_AL_PATH + " text not null," +
                C_AL_PATH_TO + " text," +
                C_AL_DIDS + " blob not null," +
                C_AL_TIME + dbcw.longType() + " not null" +
                ")" + dbcw.charSet());

        s.executeUpdate("create table if not exists " + T_SPQ + "(" +
                C_SPQ_IDX + dbcw.longType() + " primary key " + dbcw.autoIncrement() + "," +
                C_SPQ_SID + dbcw.uniqueIdType() + " not null," +
                C_SPQ_TYPE + " integer not null," +
                C_SPQ_NAME + " text" +
                ")");

        s.executeUpdate("create table if not exists " + T_UNLINKED_ROOT + "(" +
                C_UNLINKED_ROOT_SID + dbcw.uniqueIdType() + " not null primary key," +
                C_UNLINKED_ROOT_NAME + " text not null" +
                ")" + dbcw.charSet());

        s.executeUpdate("create table " + T_SC + "(" +
                C_SC_SIDX + " integer," +
                C_SC_DID + " blob," +
                "primary key (" + C_SC_SIDX + "," + C_SC_DID + ")" +
                ")" + dbcw.charSet());

        createLogicalStagingArea(s, dbcw);
    }

    @Override
    public void dump_(Statement s, PrintStream ps) throws IOException, SQLException
    {
        new CoreDatabaseDumper(_dr).dumpAll_(s, ps, true);
    }

    public static void createLogicalStagingArea(Statement s, IDBCW dbcw) throws SQLException
    {
        s.executeUpdate("create table " + T_LSA + "("
                + C_LSA_SIDX + " integer not null,"
                + C_LSA_OID + dbcw.uniqueIdType() + "not null,"
                + C_LSA_HISTORY_PATH + " text not null,"
                + "primary key(" + C_LSA_SIDX + "," + C_LSA_OID + ")"
                + ")");
    }
}
