package com.aerofs.daemon.core.phy.linked.db;

import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static com.aerofs.daemon.core.phy.linked.db.LinkedStorageSchema.*;
import static com.aerofs.lib.db.DBUtil.*;
import static com.google.common.base.Preconditions.checkState;

public class HistoryDatabase extends AbstractDatabase {
    @Inject
    public HistoryDatabase(IDBCW dbcw) {
        super(dbcw);
    }

    private final static long ROOT = -1;

    // TODO: cache?
    private final PreparedStatementWrapper _pswParent = new PreparedStatementWrapper(selectWhere(
            T_HIST_PATH, C_HIST_PATH_ID + "=?", C_HIST_PATH_NAME, C_HIST_PATH_PARENT));
    public List<String> getHistoryPath_(long id) throws SQLException {
        ArrayList<String> l = new ArrayList<>();
        while (id != ROOT) {
            try (ResultSet rs = query(_pswParent, id)) {
                if (!rs.next()) {
                    throw new AssertionError();
                }
                l.add(rs.getString(1));
                id = rs.getLong(2);

            } catch (SQLException e) {
                _pswParent.close();
                throw detectCorruption(e);
            }
        }
        Collections.reverse(l);
        return l;
    }

    private final PreparedStatementWrapper _pswChild = new PreparedStatementWrapper(selectWhere(
            T_HIST_PATH, C_HIST_PATH_PARENT + "=? and " + C_HIST_PATH_NAME + "=?", C_HIST_PATH_ID));
    private final PreparedStatementWrapper _pswAddChild = new PreparedStatementWrapper(insert(
            T_HIST_PATH, C_HIST_PATH_PARENT, C_HIST_PATH_NAME));
    public long createHistoryPath_(Path path, Trans t) throws SQLException {
        long p = ROOT;
        for (String n : path.elements()) {
            try (ResultSet rs = query(_pswChild, p, n)) {
                if (rs.next()) {
                    p = rs.getLong(1);
                    continue;
                }
            } catch (SQLException e) {
                _pswChild.close();
                throw detectCorruption(e);
            }
            checkState(1 == update(_pswAddChild, p, n));
            try (ResultSet gen = _pswAddChild.get(c()).getGeneratedKeys()) {
                checkState(gen.next());
                p = gen.getLong(1);
            } catch (SQLException e) {
                _pswAddChild.close();
                throw detectCorruption(e);
            }
        }
        return p;
    }

    private final PreparedStatementWrapper _pswInsert = new PreparedStatementWrapper(insertImpl(
            "replace into ",
            T_DELETED_FILE,
            C_DELETED_FILE_SIDX, C_DELETED_FILE_OID, C_DELETED_FILE_PATH, C_DELETED_FILE_REV)
            .toString());
    public void insertDeletedFile_(SOID soid, long path, String rev, Trans t) throws SQLException {
        checkState(1 == update(_pswInsert, soid.sidx(), soid.oid(), path, rev));
    }

    public static class DeletedFile {
        public long path;
        public String rev;
        DeletedFile(long path, String rev) {
            this.path = path;
            this.rev = rev;
        }
    }

    private final PreparedStatementWrapper _pswGet = new PreparedStatementWrapper(selectWhere(
            T_DELETED_FILE, C_DELETED_FILE_SIDX + "=? and " + C_DELETED_FILE_OID + "=?",
            C_DELETED_FILE_PATH, C_DELETED_FILE_REV));
    public @Nullable DeletedFile getDeletedFile_(SIndex sidx, OID oid) throws SQLException {
        try (ResultSet rs = query(_pswGet, sidx, oid)) {
            if (!rs.next()) return null;
            return new DeletedFile(rs.getLong(1), rs.getString(2));
        }
    }
}
