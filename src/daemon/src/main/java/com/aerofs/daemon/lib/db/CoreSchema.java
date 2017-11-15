/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib.db;

import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.injectable.InjectableDriver;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.sql.Statement;

public class CoreSchema extends SyncSchema
{
    public static final String
            // SIndex->{DID} map: for each store, maintain a set of DID having contributed ticks
            T_SC            = "sc",
            C_SC_SIDX       = "sc_s",
            C_SC_DID        = "sc_d",

            // Object Attributes
            T_OA                = "o",
            C_OA_SIDX           = "o_s",          // SIndex
            C_OA_OID            = "o_o",          // OID
            // global attributes. part of metadata
            C_OA_NAME           = "o_n",          // String
            // OID. the parent of the root folder is the SID of the parent store. this is to
            // facilitate OID-to-path lookups
            C_OA_PARENT         = "o_p",
            C_OA_TYPE           = "o_d",          // Type
            C_OA_FLAGS          = "o_l",          // int
            // local attributes. never transmitted through wire
            C_OA_FID            = "o_f",          // blob. file id (i.e. i-node number)
            C_OA_SYNCED         = "o_x",
            C_OA_OOS_CHILDREN   = "o_c",

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

            // Expulsion Table
            T_EX             = "e",
            C_EX_SIDX        = "e_s",       // SIndex
            C_EX_OID         = "e_o",       // OID

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
    public void create_(Statement s, IDBCW dbcw) throws SQLException {
        super.create_(s, dbcw);

        s.executeUpdate(
                "create table " + T_OA + "(" +
                        C_OA_SIDX + " integer not null," +
                        C_OA_OID + dbcw.uniqueIdType() + " not null," +
                        C_OA_NAME + dbcw.nameType() + " not null," +
                        C_OA_PARENT + dbcw.uniqueIdType() + " not null," +
                        C_OA_TYPE + " integer not null, " +
                        C_OA_FID + dbcw.fidType(_dr.getFIDLength()) + "," +
                        C_OA_FLAGS + " integer not null," +
                        C_OA_SYNCED + dbcw.boolType() + "default 1," +
                        C_OA_OOS_CHILDREN + dbcw.longType() + "default 0," +
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
                        C_CA_HASH + " blob,  " +
                        C_CA_MTIME + dbcw.longType() + ", " +
                        "primary key (" + C_CA_SIDX + "," + C_CA_OID + "," +
                        C_CA_KIDX + ")" +
                        ")" + dbcw.charSet());

        // for getAllNonMasterBranches_()
        createPartialCAIndex(s);

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

    }

    public static void createPartialCAIndex(Statement s) throws SQLException {
        s.executeUpdate("create index "
                + T_CA + "0 on " + T_CA + "(" + C_CA_KIDX + ")"
                +" where " + C_CA_KIDX + ">" + KIndex.MASTER.getInt());
    }
}
