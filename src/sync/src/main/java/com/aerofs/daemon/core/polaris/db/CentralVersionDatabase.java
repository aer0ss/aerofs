/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.db;

import com.aerofs.base.Loggers;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.store.IStoreDeletionOperator;
import com.aerofs.daemon.core.store.StoreDeletionOperators;
import com.aerofs.daemon.lib.db.AbstractDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.DBUtil;
import com.aerofs.lib.db.PreparedStatementWrapper;
import com.aerofs.lib.db.dbcw.IDBCW;
import com.aerofs.lib.id.SIndex;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static com.aerofs.daemon.core.polaris.db.PolarisSchema.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * Manages the database of centralized version numbers.
 *
 * In the before time a distributed version system was used which used vectors where
 * each contributing device had its own tick space. A the mighty Polaris descended upon
 * us, it became possible to adopt a much simpler scalar version number, where ticks
 * are issued by the central server.
 *
 * The versions in this table track changes to the *content* of an object, not to its
 * metadata. The precise semantic depend on the object type.
 *
 * For folders, the version is bumped every time the (name -> child) mapping changes,
 * i.e. when children are added, removed or renamed.
 *
 * For files, the version is bumped every time the size or content hash changes.
 * Timestamp-only change do NOT cause version bump.
 *
 * Contrary to the distributed version scheme which allowed an arbitrary amount of
 * content branches, the centralized system only leaves room for a single *local*
 * content branch. Local changes that conflict with a remote change already accepted
 * by the central server are rejected (and thus not propagated to remote peers). The
 * device on which that conflicting change was made thus ends up with two content
 * branches:
 *   - LOCAL (kidx = 0): the current local version (visible on the file system and on
 *     which more change can be done. For historical version this branch is known as
 *     MASTER. That may be changed once the transition away from the distributed model
 *     is complete.
 *   - REMOTE (kidx = 1): the newest known centrally accepted version
 *
 * The entries in this database only refer to valid (i.e. centrally-issued) version
 * numbers. In case of conflict, the entry corresponds to the version of the REMOTE
 * branch. Note that in some rare case there may be conflict branches with no
 * corresponding central version. See DaemonContentConflictHandler for details.
 *
 * NB: in neither case does a change to the object's own name cause a version bump.
 * Such a change would result in the version of the parent folder being bumped.
 */
public class CentralVersionDatabase extends AbstractDatabase implements IStoreDeletionOperator
{
    private final static Logger l = Loggers.getLogger(CentralVersionDatabase.class);

    @Inject
    public CentralVersionDatabase(IDBCW dbcw, StoreDeletionOperators sdo)
    {
        super(dbcw);
        sdo.addImmediate_(this);
    }

    @Override
    public void deleteStore_(SIndex sidx, Trans t) throws SQLException
    {
        if (!_dbcw.tableExists(T_VERSION)) return;
        try (Statement s = c().createStatement()) {
            s.executeUpdate("delete from " + T_VERSION
                    + " where " + C_VERSION_SIDX + "=" + sidx.getInt());
        }
    }

    private final PreparedStatementWrapper _pswGetVersion = new PreparedStatementWrapper(
            DBUtil.selectWhere(T_VERSION,
                    C_VERSION_SIDX + "=? and " + C_VERSION_OID + "=?", C_VERSION_TICK));
    public @Nullable Long getVersion_(SIndex sidx, OID oid) throws SQLException
    {
        try (ResultSet rs = query(_pswGetVersion, sidx.getInt(), oid.getBytes())) {
            return rs.next() ? rs.getLong(1) : null;
        }
    }

    private final PreparedStatementWrapper _pswSetVersion = new PreparedStatementWrapper(
            DBUtil.updateWhere(T_VERSION,
                    C_VERSION_SIDX + "=? and " + C_VERSION_OID + "=?", C_VERSION_TICK));
    private final PreparedStatementWrapper _pswInsertVersion = new PreparedStatementWrapper(
            DBUtil.insert(T_VERSION, C_VERSION_SIDX, C_VERSION_OID, C_VERSION_TICK));
    public void setVersion_(SIndex sidx, OID oid, long version, Trans t) throws SQLException
    {
        l.debug("{}{} = {}", sidx, oid, version);
        int n = update(_pswSetVersion, version, sidx.getInt(), oid.getBytes());
        if (n == 1) return;
        // FIXME: update fails sometimes, WTF?!?!
        // current hypothesis is that lambdas were doing funky stuff to captured variables
        // keeping this log line around in case the issue resurfaces...
        if (version > 1) {
            l.warn("{}{} {} {} {}", sidx, oid, version, getVersion_(sidx, oid), n);
        }
        checkState(1 == update(_pswInsertVersion, sidx.getInt(), oid.getBytes(), version));
    }

    private final PreparedStatementWrapper _pswDeleteVersion = new PreparedStatementWrapper(
            DBUtil.deleteWhereEquals(T_VERSION, C_VERSION_SIDX, C_VERSION_OID));
    public boolean deleteVersion_(SIndex sidx, OID oid, Trans t) throws SQLException
    {
        return 1 == update(_pswDeleteVersion, sidx.getInt(), oid.getBytes());
    }
}
