package com.aerofs.daemon.lib.db.ver;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Beyond the methods of IVersionDatabase, this interface represents
 * persistent storage of native versions
 */
public interface INativeVersionDatabase extends IVersionDatabase<NativeTickRow>
{
    void addLocalVersion_(SOCKID k, Version v, Trans t)
            throws SQLException;

    void addKMLVersion_(SOCID socid, Version v, Trans t)
            throws SQLException;

    void deleteLocalVersion_(SOCKID k, Version v, Trans t)
            throws SQLException;

    void deleteKMLVersion_(SOCID socid, Version v, Trans t)
            throws SQLException;

    @Nullable Tick getLocalTick_(SOCKID k)
            throws SQLException;

    @Nonnull Version getKMLVersion_(SOCID socid)
            throws SQLException;

    @Nonnull Version getLocalVersion_(SOCKID k)
            throws SQLException;

    /**
     * @return the union of the local version of all the branches.
     */
    @Nonnull Version getAllLocalVersions_(SOCID socid) throws SQLException;

    /**
     * @return the union of KML and the local version of all the branches.
     * Note: this call may be expensive, so use it sparingly.
     */
    @Nonnull Version getAllVersions_(SOCID socid)
            throws SQLException;

    /**
     * N.B. only VersionAssistant should call these methods
     */
    void updateMaxTicks_(SOCID socid, Trans t) throws SQLException;
    void deleteMaxTicks_(SOCID socid, Trans t) throws SQLException;

    /**
     * @pre target may not have any local versions (it may have KMLs)
     */
    void moveAllLocalVersions_(SOCID alias, SOCID target, Trans t) throws SQLException;

    /**
     * When deleting/expelling content, we can condense
     *    - n+1 getLocalVersion
     *    - n deleteLocalVersion
     *    - 1 addLocalVersion
     *    - 1 updateMaxticks
     * into
     *    - 1 deleteAllVersions
     *    - 1 moveMaxTicksToKML
     * by leveraging the invariant that max ticks is always the union
     * of all ticks (including KMLs).
     *
     * This saves a fair amount of CPU and disk I/O when deleting/expelling
     * large number of files.
     */
    void deleteAllVersions_(SOCID socid, Trans t) throws SQLException;
    void moveMaxTicksToKML_(SOCID socid, Trans t) throws SQLException;
}
