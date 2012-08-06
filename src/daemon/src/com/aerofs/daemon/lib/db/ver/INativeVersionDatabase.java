package com.aerofs.daemon.lib.db.ver;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOCKID;

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

    Tick getLocalTick_(SOCKID k)
            throws SQLException;

    Version getKMLVersion_(SOCID socid)
            throws SQLException;

    Version getLocalVersion_(SOCKID k)
            throws SQLException;

    /**
     * @return the union of the local version of all the branches.
     */
    Version getAllLocalVersions_(SOCID socid) throws SQLException;

    /**
     * @return the union of KML and the local version of all the branches.
     * Note: this call may be expensive, so use it sparingly.
     */
    Version getAllVersions_(SOCID socid)
            throws SQLException;

    /**
     * N.B. only VersionAssistant should call these methods
     */
    void updateMaxTicks_(SOCID socid, Trans t) throws SQLException;
    void deleteMaxTicks_(SOCID socid, Trans t) throws SQLException;
}
