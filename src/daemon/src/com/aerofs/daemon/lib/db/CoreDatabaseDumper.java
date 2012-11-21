package com.aerofs.daemon.lib.db;

import static com.aerofs.daemon.lib.db.CoreSchema.*;

import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.aerofs.daemon.core.collector.CollectorSeq;
import com.aerofs.daemon.core.collector.SenderFilterIndex;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.injectable.InjectableDriver;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

/**
 * This class dumps the core database in an application specific way, e.g. it uses short format for
 * UniqueIDs and custom format for bloom filters.
 */
public class CoreDatabaseDumper extends AbstractDatabase
{
    private final InjectableDriver _dr;

    public CoreDatabaseDumper(IDBCW dbcw, InjectableDriver dr)
    {
        super(dbcw);
        _dr = dr;
    }

    @Inject
    public CoreDatabaseDumper(CoreDBCW dbcw, InjectableDriver dr)
    {
        super(dbcw.get());
        _dr = dr;
    }

    // Instead of having separate method for each table this way,
    // caller could instantiate class of required table and call over-ridden
    // dump method. This would require having separate dump class for each
    // table in Database.
    public void dumpAll_(PrintStream ps, boolean formal)
        throws SQLException
    {
        dumpSID_(ps, formal);
        ps.println();
        dumpStore_(ps);
        ps.println();
        dumpStoreParent_(ps);
        ps.println();
        dumpPrefix_(ps, formal);
        ps.println();
        dumpVer_(ps, formal);
        ps.println();
        dumpMaxTick_(ps, formal);
        ps.println();
        dumpIV_(ps, formal);
        ps.println();
        dumpAttr_(ps, formal);
        ps.println();
        dumpContent_(ps, formal);
        ps.println();
        dumpKwlg_(ps, formal);
        ps.println();
        dumpIK_(ps, formal);
        ps.println();
        dumpGreatestTick_(ps);
        ps.println();
        dumpSF_(ps);
        ps.println();
        dumpSD_(ps);
        ps.println();
        dumpCF_(ps);
        ps.println();
        dumpCS_(ps);
        ps.println();
        dumpAlias_(ps, formal);
        ps.println();
        dumpNativeBackupTicks_(ps);
        ps.println();
        dumpImmigrantBackupTicks_(ps, formal);
        ps.println();
        dumpPulledDevice(ps, formal);
        ps.println();
        dumpExpulsion_(ps, formal);
        ps.println();
        dumpACL_(ps);
        ps.println();
        dumpEpoch_(ps);
        ps.println();
        dumpD2U_(ps, formal);
        ps.println();
        dumpAL_(ps, formal);
        ps.println();
        dumpSyncStatBootstrap_(ps, formal);
        ps.println();
    }

    private void dumpKwlg_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_KWLG_SIDX + "," + C_KWLG_DID + "," + C_KWLG_TICK
                        + " from " + T_KWLG);
        ps.println("============ " + T_KWLG + " ===============");
        ps.println(C_KWLG_SIDX + "\t" + C_KWLG_DID + "\t" + C_KWLG_TICK);
        ps.println("------------------------------");

        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            DID did = new DID(rs.getBytes(2));
            Tick tk = new Tick(rs.getInt(3));

            if (formal) {
                ps.println(sidx + "\t" + did.toStringFormal() + '\t' + tk);
            } else {
                ps.println(sidx + "\t" + did + '\t' + tk);
            }
        }
    }

    private void dumpIK_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_IK_SIDX + "," + C_IK_IMM_DID + "," + C_IK_IMM_TICK
                        + " from " + T_IK);
        ps.println("============ " + T_IK + " ==============");
        ps.println(C_IK_SIDX + "\t" + C_IK_IMM_DID + "\t" + C_IK_IMM_TICK);
        ps.println("------------------------------");

        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            DID did = new DID(rs.getBytes(2));
            Tick tk = new Tick(rs.getInt(3));

            if (formal) {
                ps.println(sidx + "\t" + did.toStringFormal() + '\t' + tk);
            } else {
                ps.println(sidx + "\t" + did + '\t' + tk);
            }
        }
    }

    public void dumpAttr_(PrintStream ps, boolean formal) throws SQLException
    {
        StringBuilder sbNullFID = new StringBuilder("null");
        for (int i = 0; i < _dr.getFIDLength() * 2 - 4; i++) sbNullFID.append(' ');

        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_OA_SIDX + "," + C_OA_OID + "," + C_OA_NAME
                        + "," + C_OA_PARENT + "," + C_OA_TYPE + "," +
                        C_OA_FLAGS + "," + C_OA_FID + "," + C_OA_SYNC + "," + C_OA_AG_SYNC +
                        " from " + T_OA +
                        " order by " + C_OA_SIDX + ", " + C_OA_OID);
        ps.println("================== " + T_OA + " =====================");
        ps.println(C_OA_SIDX + "\t" + C_OA_OID + "\t" + C_OA_PARENT
                + "\t" + C_OA_TYPE + "\t" + C_OA_FLAGS + "\t" + C_OA_FID + "\t\tcrc " + C_OA_NAME
                + "\t" + C_OA_SYNC + "\t" + C_OA_AG_SYNC);
        ps.println("------------------------------------------");

        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID oid = new OID(rs.getBytes(2));

            String name = rs.getString(3);
            if (rs.wasNull()) name = null;

            rs.getBytes(4);
            OID parent = rs.wasNull() ? null : new OID(rs.getBytes(4));

            int type = rs.getInt(5);

            Integer flags = rs.getInt(6);
            if (rs.wasNull()) flags = null;

            byte[] bs = rs.getBytes(7);
            FID fid = rs.wasNull() ? null : new FID(bs);
            String strFID = fid == null ? sbNullFID.toString() : fid.toString();

            byte[] sync = rs.getBytes(8);
            BitVector bv = sync != null ? new BitVector(sync.length * 8, sync) : new BitVector();

            byte[] agsync = rs.getBytes(9);
            CounterVector cv = agsync != null ? CounterVector.fromByteArrayCompressed(agsync)
                    : new CounterVector();

            if (formal) {
                ps.println(sidx.toString() + '\t' + oid.toStringFormal() + '\t' +
                        parent.toStringFormal() + '\t' + + type + '\t' + flags + '\t' + strFID +
                        '\t' + Util.crc32(name) + " " + name + '\t' + bv + '\t' + cv);
            } else {
                ps.println(sidx.toString() + '\t' + oid + '\t' + parent + '\t'
                        + type + '\t' + flags + '\t' + strFID + '\t' + '\t'
                        + Util.crc32(name) + " " + name + '\t' + bv + '\t' + cv);
            }
        }
    }

    public void dumpSyncStatBootstrap_(PrintStream ps, boolean formal) throws SQLException
    {
        StringBuilder sbNullFID = new StringBuilder("null");
        for (int i = 0; i < _dr.getFIDLength() * 2 - 4; i++) sbNullFID.append(' ');

        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_SSBS_SIDX + "," + C_SSBS_OID + " from " + T_SSBS +
                        " order by " + C_SSBS_SIDX + ", " + C_SSBS_OID);
        ps.println("================== " + T_SSBS + " =====================");
        ps.println(C_SSBS_SIDX + "\t" + C_SSBS_OID);
        ps.println("------------------------------------------");

        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID oid = new OID(rs.getBytes(2));

            if (formal) {
                ps.println(sidx.toString() + '\t' + oid.toStringFormal());
            } else {
                ps.println(sidx.toString() + '\t' + oid);
            }
        }
    }

    private void dumpContent_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_CA_SIDX + "," + C_CA_OID + "," + C_CA_KIDX + ","
                        + C_CA_LENGTH + "," + C_CA_MTIME +  "," + C_CA_HASH +
                        " from " + T_CA + " order by " + C_CA_SIDX + ", " +
                        C_CA_OID + ", " + C_CA_KIDX
                        );
        ps.println("====================== " + T_CA + " =========================");
        ps.println(C_CA_SIDX + "\t" + C_CA_OID + "\t" + C_CA_KIDX + "\t"
                + C_CA_LENGTH + "\t" + C_CA_MTIME + "\t\t" + C_CA_HASH);
        ps.println("--------------------------------------------------");

        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID oid = new OID(rs.getBytes(2));
            KIndex kidx = new KIndex(rs.getInt(3));
            long len = rs.getLong(4);
            long mtime = rs.getLong(5);
            byte[] hash = rs.getBytes(6);

            // Only first 4 bytes of the hash will be printed.
            String hStr = hash != null ? "<" + Util.hexEncode(hash, 0, 4) + ">" : "null";
            if (formal) {
                ps.println(sidx.toString() + '\t' + oid.toStringFormal() + '\t' + kidx + '\t'
                        + len + '\t' + mtime + '\t' + hStr);
            } else {
                ps.println(sidx.toString() + '\t' + oid + '\t' + kidx + '\t'
                        + len + '\t' + mtime + '\t' + hStr);
            }
        }
    }

    private void dumpPrefix_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_PRE_SIDX + "," + C_PRE_OID
                        + "," + C_PRE_KIDX + "," + C_PRE_DID + "," + C_PRE_TICK
                        + " from " + T_PRE);
        ps.println("=================== " + T_PRE + " =====================");
        ps.println(C_PRE_SIDX + "\t" + C_PRE_OID + "\t"
                + C_PRE_KIDX + "\t" + C_PRE_DID + "\t" + C_PRE_TICK);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID oid = new OID(rs.getBytes(2));
            KIndex kidx = new KIndex(rs.getInt(3));
            DID did = new DID(rs.getBytes(4));
            Tick tick = new Tick(rs.getLong(5));

            if (formal) {
                ps.println("" + sidx + '\t' + oid.toStringFormal() + '\t' + kidx + '\t'
                        + did.toStringFormal() + '\t' + tick);
            } else {
                ps.println("" + sidx + '\t' + oid + '\t' + kidx + '\t'
                        + did + '\t' + tick);
            }
        }
    }

    public void dumpVer_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_VER_SIDX + "," + C_VER_OID + "," + C_VER_CID
                        + "," + C_VER_DID + "," + C_VER_TICK + "," + C_VER_KIDX
                        + " from " + T_VER + " order by "
                        + C_VER_SIDX + ", " + C_VER_OID + ", " + C_VER_KIDX + ", "
                        + C_VER_CID + ", " + C_VER_DID);
        ps.println("=================== " + T_VER + " =====================");
        ps.println(C_VER_SIDX + "\t" + C_VER_OID + "\t" + C_VER_CID + "\t"
                + C_VER_DID + "\t" + C_VER_TICK + "\t" + C_VER_KIDX);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID oid = new OID(rs.getBytes(2));
            CID cid = new CID(rs.getInt(3));
            DID did = new DID(rs.getBytes(4));
            Tick tick = new Tick(rs.getLong(5));
            KIndex kidx = new KIndex(rs.getInt(6));

            if (formal) {
                ps.println("" + sidx + '\t' + oid.toStringFormal() + '\t' + cid
                        + '\t' + did.toStringFormal() + '\t'
                        + tick + '\t' + kidx);
            } else {
                ps.println("" + sidx + '\t' + oid + '\t' + cid + '\t' + did + '\t'
                        + tick + '\t' + kidx);
            }
        }
    }

    private void dumpMaxTick_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_MAXTICK_SIDX + "," + C_MAXTICK_OID + "," + C_MAXTICK_CID
                        + "," + C_MAXTICK_DID + "," + C_MAXTICK_MAX_TICK
                        + " from " + T_MAXTICK);
        ps.println("=================== " + T_MAXTICK + " =====================");
        ps.println(C_MAXTICK_SIDX + "\t" + C_MAXTICK_OID + "\t" + C_MAXTICK_CID + "\t"
                + C_MAXTICK_DID + "\t" + C_MAXTICK_MAX_TICK);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID oid = new OID(rs.getBytes(2));
            CID cid = new CID(rs.getInt(3));
            DID did = new DID(rs.getBytes(4));
            Tick tick = new Tick(rs.getLong(5));

            if (formal) {
                ps.println("" + sidx + '\t' + oid.toStringFormal() + '\t' + cid
                        + '\t' + did.toStringFormal() + '\t' + tick);
            } else {
                ps.println("" + sidx + '\t' + oid + '\t' + cid + '\t' + did + '\t'
                        + tick);
            }
        }
    }

    private void dumpGreatestTick_(PrintStream ps) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_GT_NATIVE + "," + C_GT_IMMIGRANT + " from " + T_GT);
        ps.println("============ " + T_GT + " ==============");
        ps.println(C_GT_NATIVE + "\t" + C_GT_IMMIGRANT);
        ps.println("------------------------------");
        while (rs.next()) {
            Tick tick = new Tick(rs.getLong(1));
            Tick immTick = new Tick(rs.getLong(2));

            ps.println(tick + "\t" + immTick);
        }
    }

    private void dumpIV_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_IV_SIDX + "," + C_IV_IMM_DID + "," + C_IV_IMM_TICK +
                    "," + C_IV_OID + "," + C_IV_CID + "," + C_IV_DID + "," +
                        C_IV_TICK + " from " + T_IV);
        ps.println("=================== " + T_IV + " =====================");
        ps.println(C_IV_SIDX + "\t" + C_IV_IMM_DID + "\t" + C_IV_IMM_TICK + "\t" +
                C_IV_OID + "\t" + C_IV_CID + "\t" + C_IV_DID + "\t" + C_IV_TICK);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            DID immdid = new DID(rs.getBytes(2));
            Tick immtick = new Tick(rs.getLong(3));
            OID oid = new OID(rs.getBytes(4));
            CID cid = new CID(rs.getInt(5));
            DID did = new DID(rs.getBytes(6));
            Tick tick = new Tick(rs.getLong(7));

            if (formal) {
                ps.println("" + sidx + '\t' + immdid.toStringFormal() + '\t' +
                        immtick + '\t' + oid.toStringFormal() + '\t' + cid
                        + '\t' + did.toStringFormal() + '\t'
                        + tick);
            } else {
                ps.println("" + sidx + '\t' + immdid + '\t' + immtick + '\t' +
                        oid + '\t' + cid + '\t' + did + '\t' + tick);
            }
        }
    }

    private void dumpSID_(PrintStream ps, boolean formal)
            throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_SID_SIDX + "," + C_SID_SID + " from " + T_SID);
        ps.println("================== " + T_SID + " =====================");
        ps.println(C_SID_SIDX + "\t" + C_SID_SID);
        ps.println("------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            SID sid = new SID(rs.getBytes(2));
            ps.println(sidx + "\t" + (formal ? sid.toStringFormal() : sid));
        }
    }

    private void dumpStore_(PrintStream ps)
            throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_STORE_SIDX + "," + C_STORE_DIDS +
                " from " + T_STORE);
        ps.println("================== " + T_STORE + " =====================");
        ps.println(C_STORE_SIDX + "\t" + C_STORE_DIDS);
        ps.println("------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            byte[] d = rs.getBytes(2);
            StringBuilder bd = new StringBuilder();
            for (int i = 0; i < (d != null ? d.length / DID.LENGTH : 0); ++i) {
                bd.append(new DID(Arrays.copyOfRange(d, i * DID.LENGTH, (i+1) * DID.LENGTH))
                        .toStringFormal());
                bd.append(" ");
            }
            ps.println(sidx + "\t" + bd.toString());
        }
    }

    private void dumpStoreParent_(PrintStream ps)
            throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_SH_SIDX + "," + C_SH_PARENT_SIDX +
                        " from " + T_SH);
        ps.println("================== " + T_SH + " =====================");
        ps.println(C_SH_SIDX + "\t" + C_SH_PARENT_SIDX);
        ps.println("------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            SIndex parent = new SIndex(rs.getInt(2));
            ps.println(sidx + "\t" + parent);
        }
    }

    private void dumpSF_(PrintStream ps) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_SF_SIDX + "," + C_SF_SFIDX + ","
                        + C_SF_FILTER + " from " + T_SF);
        ps.println("================== " + T_SF + " =====================");
        ps.println(C_SF_SIDX + "\t" + C_SF_SFIDX + "\t" + C_SF_FILTER);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            SenderFilterIndex sfidx = new SenderFilterIndex(rs.getLong(2));
            BFOID filter = new BFOID(rs.getBytes(3));

            ps.println(sidx + "\t" + sfidx + '\t' + filter);
        }
    }

    private void dumpSD_(PrintStream ps) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_SD_SIDX + "," + C_SD_DID + ","
                        + C_SD_SFIDX + " from " + T_SD);
        ps.println("================== " + T_SD + " =====================");
        ps.println(C_SD_SIDX + "\t" + C_SD_DID + "\t" + C_SD_SFIDX);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            DID did = new DID(rs.getBytes(2));
            SenderFilterIndex sfidx = new SenderFilterIndex(rs.getLong(3));

            ps.println(sidx + "\t" + did + '\t' + sfidx);
        }
    }

    private void dumpCF_(PrintStream ps) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_CF_SIDX + "," + C_CF_DID + ","
                        + C_CF_FILTER + " from " + T_CF);
        ps.println("================== " + T_CF + " =====================");
        ps.println(C_CF_SIDX + "\t" + C_CF_DID + "\t" + C_CF_FILTER);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            DID did = new DID(rs.getBytes(2));
            BFOID filter = new BFOID(rs.getBytes(3));

            ps.println(sidx + "\t" + did + '\t' + filter);
        }
    }

    private void dumpCS_(PrintStream ps) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_CS_CS + "," + C_CS_SIDX + "," + C_CS_OID + ","
                        + C_CS_CID + " from " + T_CS);
        ps.println("================== " + T_CS + " =====================");
        ps.println(C_CS_CS + "\t" + C_CS_SIDX + "\t" + C_CS_OID + "\t" + C_CS_CID);
        ps.println("--------------------------------------------");
        while (rs.next()) {
            CollectorSeq cs = new CollectorSeq(rs.getLong(1));
            SIndex sidx = new SIndex(rs.getInt(2));
            OID oid = new OID(rs.getBytes(3));
            CID cid = new CID(rs.getInt(4));

            ps.println(cs + "\t" + sidx + '\t' + oid + '\t' + cid);
        }
    }

    public void dumpAlias_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_ALIAS_SIDX + ", " + C_ALIAS_SOURCE_OID +  ", " +
                C_ALIAS_TARGET_OID + " from " + T_ALIAS +
                " order by " + C_ALIAS_SIDX + ", " + C_ALIAS_SOURCE_OID + ", " +
                C_ALIAS_TARGET_OID);

        ps.println("================== " + T_ALIAS + " =====================");
        ps.println(C_ALIAS_SIDX + "\t" + C_ALIAS_SOURCE_OID + "\t\t" + C_ALIAS_TARGET_OID);
        ps.println("---------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID src = new OID(rs.getBytes(2));
            OID target = new OID(rs.getBytes(3));

            ps.println(sidx + "\t" + (formal ? src.toStringFormal() : src) +
                    "\t\t" + (formal ? target.toStringFormal() : target));
        }
    }

    public void dumpExpulsion_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_EX_SIDX + ", " + C_EX_OID + " from " + T_EX);

        ps.println("================== " + T_EX + " =====================");
        ps.println(C_EX_SIDX + "\t" + C_EX_OID);
        ps.println("---------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID oid = new OID(rs.getBytes(2));

            ps.println(sidx + "\t" + (formal ? oid.toStringFormal() : oid));
        }
    }

    public void dumpACL_(PrintStream ps) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_ACL_SIDX + ", " + C_ACL_SUBJECT + ", " + C_ACL_ROLE + " from " +
                        T_ACL);

        ps.println("================== " + T_ACL + " =====================");
        ps.println(C_ACL_SIDX + "\t" + C_ACL_ROLE + "\t" + C_ACL_SUBJECT);
        ps.println("---------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            String subject = rs.getString(2);
            Role role = Role.values()[rs.getInt(3)];

            ps.println(sidx + "\t" + role + "\t" + subject);
        }
    }

    private void dumpEpoch_(PrintStream ps) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_EPOCH_ACL + "," + C_EPOCH_SYNC_PULL + "," + C_EPOCH_SYNC_PUSH +
                " from " + T_EPOCH);
        ps.println("============ " + T_EPOCH + " ==============");
        ps.println(C_EPOCH_ACL + "\t" + C_EPOCH_SYNC_PULL + "\t" + C_EPOCH_SYNC_PUSH);
        ps.println("------------------------------");
        while (rs.next()) {
            long acl = rs.getInt(1);
            long syncPull = rs.getLong(2);
            long syncPush = rs.getLong(3);
            ps.println(acl + "\t" + syncPull + "\t" + syncPush);
        }
    }

    public void dumpNativeBackupTicks_(PrintStream ps) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_BKUPT_SIDX + ", " + C_BKUPT_OID + ", " +
                C_BKUPT_CID + ", " + C_BKUPT_TICK + " from " + T_BKUPT +
                " order by " + C_BKUPT_SIDX + ", " + C_BKUPT_OID + ", " +
                C_BKUPT_CID
                );
        ps.println("================== " + T_BKUPT + " =====================");
        ps.println(C_BKUPT_SIDX + "\t" + C_BKUPT_OID + "\t" + C_BKUPT_CID +
                   "\t" + C_BKUPT_TICK);
        ps.println("--------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            OID oid = new OID(rs.getBytes(2));
            CID cid = new CID(rs.getInt(3));
            Tick tick = new Tick(rs.getLong(4));

            ps.println(sidx + "\t" + oid + "\t" + cid + "\t" + tick);
        }
    }


    private void dumpImmigrantBackupTicks_(PrintStream ps, boolean formal)
            throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_IBT_SIDX + "," + C_IBT_IMM_TICK +
                    "," + C_IBT_OID + "," + C_IBT_CID + "," + C_IBT_DID + "," +
                        C_IBT_TICK + " from " + T_IBT);
        ps.println("=================== " + T_IBT + " =====================");
        ps.println(C_IBT_SIDX + "\t" + C_IBT_IMM_TICK + "\t" + C_IBT_OID + "\t"
                   + C_IBT_CID + "\t" + C_IBT_DID + "\t" + C_IBT_TICK);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            Tick immtick = new Tick(rs.getLong(2));
            OID oid = new OID(rs.getBytes(3));
            CID cid = new CID(rs.getInt(4));
            DID did = new DID(rs.getBytes(5));
            Tick tick = new Tick(rs.getLong(6));

            if (formal) {
                ps.println("" + sidx + '\t' + immtick + '\t'
                        + oid.toStringFormal() + '\t' + cid
                        + '\t' + did.toStringFormal() + '\t' + tick);
            } else {
                ps.println("" + sidx + '\t' + immtick + '\t' +
                        oid + '\t' + cid + '\t' + did + '\t' + tick);
            }
        }
    }

    private void dumpPulledDevice(PrintStream ps, boolean formal)
            throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_PD_SIDX + "," + C_PD_DID +
                        " from " + T_PD);
        ps.println("=================== " + T_PD + " =====================");
        ps.println(C_PD_SIDX + "\t" + C_PD_DID);
        ps.println("-------------------------------------------");
        while (rs.next()) {
            SIndex sidx = new SIndex(rs.getInt(1));
            DID did = new DID(rs.getBytes(2));

            if (formal) {
                ps.println("" + sidx + '\t' + did.toStringFormal());
            } else {
                ps.println("" + sidx + '\t' + did);
            }
        }
    }

    private void dumpD2U_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_D2U_DID + "," + C_D2U_USER + " from " + T_D2U);
        ps.println("============ " + T_D2U + " ===============");
        ps.println(C_D2U_DID + "\t" + C_D2U_USER);
        ps.println("------------------------------");

        while (rs.next()) {
            DID did = new DID(rs.getBytes(1));
            String user = rs.getString(2);

            if (formal) {
                ps.println(did.toStringFormal() + '\t' + user);
            } else {
                ps.println(did.toString() + '\t' + user);
            }
        }
    }

    private void dumpAL_(PrintStream ps, boolean formal) throws SQLException
    {
        ResultSet rs = c().createStatement().executeQuery(
                "select " + C_AL_IDX + "," + C_AL_SIDX + "," + C_AL_OID + "," +
                        C_AL_TYPE + "," + C_AL_TIME + "," + C_AL_DIDS + "," + C_AL_PATH + ","
                        + C_AL_PATH_TO + " from " + T_AL);
        ps.println("============ " + T_AL + " ===============");
        ps.println(C_AL_IDX + "\t" + C_AL_SIDX + "\t" + C_AL_OID + "\t" + C_AL_TYPE + "\t" +
                C_AL_TIME + "\t\t\t" + C_AL_DIDS + "\t" + C_AL_PATH + "\t" + C_AL_PATH_TO);
        ps.println("------------------------------");

        while (rs.next()) {
            long idx = rs.getLong(1);
            SIndex sidx = new SIndex(rs.getInt(2));
            OID oid = new OID(rs.getBytes(3));
            int activities = rs.getInt(4);
            Date time = new Date(rs.getLong(5));
            Set<DID> dids = ActivityLogDatabase.convertDIDs(rs.getBytes(6));
            String path = rs.getString(7);
            String pathTo = rs.getString(8);
            if (pathTo == null) pathTo = "-";

            String strTime = new SimpleDateFormat("yyMMdd:HHmmss.SSS").format(time);

            if (formal) {
                List<String> didStrings = Lists.newArrayListWithCapacity(dids.size());
                for (DID did : dids) didStrings.add(did.toStringFormal());

                ps.println(idx + "\t" + sidx + "\t" + oid.toStringFormal() + '\t' +
                        Integer.toHexString(activities) + '\t' + strTime + '\t' +
                        Joiner.on(':').join(didStrings) + '\t' +
                        path + '\t' + pathTo);
            } else {
                ps.println(idx + "\t" + sidx + "\t" + oid + '\t' + Integer.toHexString(activities) +
                        '\t' + strTime + '\t' + Joiner.on(':').join(dids) + '\t' + path +
                        '\t' + pathTo);
            }
        }
    }
}
