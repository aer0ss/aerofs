package com.aerofs.daemon.lib.db.ver;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;

import javax.annotation.Nonnull;

import static com.aerofs.daemon.lib.db.CoreSchema.T_GT;

/**
 * Those IVersionDatabase methods that share a common code path between
 * Immigrant and Native versions are implemented here as Template algorithms
 * (see the Template pattern). The only templating required by subclasses is
 * to specify the db tables and columns of interest, specific to the subclass
 * (e.g. ImmigrantVersionDatabase would return the Immigrant Knowledge table)
 *
 * @author markj
 *
 * @param <E>
 */
public abstract class AbstractVersionDatabase<E extends AbstractTickRow> extends AbstractDatabase
    implements IVersionDatabase<E>
{
    private final String _c_gt;

    private final String _t_k;
    private final String _c_k_sidx;
    private final String _c_k_did;
    private final String _c_k_tick;

    private final String _t_ver;
    private final String _c_ver_sidx;
    private final String _c_ver_oid;
    private final String _c_ver_cid;
    private final String _c_ver_did;
    private final String _c_ver_tick;

    /**
     * @param c_gt the column of interest in the Greatest Ticks table (i.e. Native or Immigrant)
     * @param t_k the knowledge table for this version db (Immigrant or Native)
     * @param c_k_did the DID column extracted for knowledge from the kwlg table
     * @param c_k_tick the Tick column extracted for knowledge from the kwlg table
     * @param t_ver the version table used to get all Version DIDs
     * @param c_ver_did the DID column of interest when requesting all Version DIDs
     * @param t_bkupt the version-backup table to backup local ticks on store deletion
     */
    protected AbstractVersionDatabase(IDBCW dbcw,
            String c_gt,
            String t_k,
            String c_k_sidx,
            String c_k_did,
            String c_k_tick,
            String t_ver,
            String c_ver_did,
            String c_ver_sidx,
            String c_ver_tick,
            String c_ver_cid,
            String c_ver_oid)
    {
        super(dbcw);
        _c_gt = c_gt;
        _t_k = t_k;
        _c_k_sidx = c_k_sidx;
        _c_k_did = c_k_did;
        _c_k_tick = c_k_tick;
        _t_ver = t_ver;
        _c_ver_did = c_ver_did;
        _c_ver_sidx = c_ver_sidx;
        _c_ver_tick = c_ver_tick;
        _c_ver_cid = c_ver_cid;
        _c_ver_oid = c_ver_oid;
    }

    @Override
    public @Nonnull Tick getGreatestTick_() throws SQLException
    {
        Statement s = null;
        try {
            // we don't cache ps as the method is not called frequently
            s = c().createStatement();
            ResultSet rs = s.executeQuery(
                    "select " + _c_gt + " from " + T_GT);
            try {
                Util.verify(rs.next());
                Tick tick = new Tick(rs.getLong(1));
                assert !rs.next();
                return tick;
            } finally {
                rs.close();
            }
        } finally {
            DBUtil.close(s);
        }
    }

    private PreparedStatement _psSGT;
    @Override
    public void setGreatestTick_(Tick tick, Trans t) throws SQLException
    {
        try {
            if (_psSGT == null) _psSGT = c()
                    .prepareStatement("update " + T_GT + " set " +
                            _c_gt + "=?");

            _psSGT.setLong(1, tick.getLong());
            _psSGT.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psSGT);
            _psSGT = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psGK;
    @Override
    public @Nonnull Version getKnowledgeExcludeSelf_(SIndex sidx) throws SQLException
    {
        try {
            if (_psGK == null) _psGK = c().prepareStatement("select "
                    + _c_k_did + "," + _c_k_tick + " from " + _t_k
                    + " where " + _c_k_sidx + "=?");

            _psGK.setInt(1, sidx.getInt());

            ResultSet rs = _psGK.executeQuery();
            try {
                Version v = Version.empty();
                while (rs.next()) {
                    v.set_(new DID(rs.getBytes(1)), rs.getLong(2));
                }
                return v;
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psGK);
            _psGK = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psAddKwlg;
    @Override
    public void addKnowledge_(SIndex sidx, DID did, Tick tick, Trans t)
            throws SQLException
    {
        try {
            if (_psAddKwlg == null) _psAddKwlg = c()
                    .prepareStatement("replace into " + _t_k + "("
                            + _c_k_sidx + "," + _c_k_did + ","
                            + _c_k_tick + ") values (?,?,?)");

            _psAddKwlg.setInt(1, sidx.getInt());
            _psAddKwlg.setBytes(2, did.getBytes());
            _psAddKwlg.setLong(3, tick.getLong());
            _psAddKwlg.executeUpdate();

        } catch (SQLException e) {
            DBUtil.close(_psAddKwlg);
            _psAddKwlg = null;
            throw detectCorruption(e);
        }
    }

    private PreparedStatement _psISK;
    @Override
    public boolean isTickKnown_(SOCID socid, DID did, Tick tick)
        throws SQLException
    {
        // N.B. the implementation assumes that KIndex.INVALID is smaller than
        // any valid kindex
        try {
            if (_psISK == null) _psISK = c()
                    .prepareStatement("select count(*) from " + _t_ver +
                            " where "
                            + _c_ver_sidx + "=? and " + _c_ver_oid + "=? and "
                            + _c_ver_cid + "=? and " + _c_ver_did + "=? and "
                            + _c_ver_tick + ">=?");

            _psISK.setInt(1, socid.sidx().getInt());
            _psISK.setBytes(2, socid.oid().getBytes());
            _psISK.setInt(3, socid.cid().getInt());
            _psISK.setBytes(4, did.getBytes());
            _psISK.setLong(5, tick.getLong());
            ResultSet rs = _psISK.executeQuery();
            try {
                Util.verify(rs.next());
                return rs.getInt(1) > 0;
            } finally {
                rs.close();
            }

        } catch (SQLException e) {
            DBUtil.close(_psISK);
            _psISK = null;
            throw detectCorruption(e);
        }
    }
}
