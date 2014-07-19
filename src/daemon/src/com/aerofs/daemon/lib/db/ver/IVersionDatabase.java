package com.aerofs.daemon.lib.db.ver;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;

import javax.annotation.Nonnull;
import java.sql.SQLException;

/**
 * Native and Immigrant versions share some similarities in getters/setters.
 * This interface specifies the type-agnostic methods for persistent storage of
 * versions.
 *
 * @param <E> specifies the subclass of TickRow returned in getTicks:
 *            - NativeTickRow or ImmigrantTickRow
 *
 * @author markj
 */
public interface IVersionDatabase<E extends AbstractTickRow>
{
    @Nonnull Tick getGreatestTick_() throws SQLException;

    void setGreatestTick_(Tick tick, Trans t) throws SQLException;

    /**
     * @return the entries with ticks greater than from. the returned ticks
     * must be in ascending order
     */
    @Nonnull IDBIterator<E> getMaxTicks_(SIndex sidx, DID did, Tick from)
        throws SQLException;

    /**
     * @return true iff a tuple <socid, did, t> exists in the database where t >= tick
     */
    boolean isTickKnown_(SOCID socid, DID did, Tick tick)
            throws SQLException;

    @Nonnull Version getKnowledgeExcludeSelf_(SIndex sidx)
            throws SQLException;

    void addKnowledge_(SIndex sidx, DID did, Tick tick, Trans t)
            throws SQLException;

    /*
     * On Backing up Local Device Ticks During Store Deletion
     * -------------------------------------------------------------------------
     * When a store is locally deleted, all data related to the store is deleted
     * from DB tables that are used in typical operations. This purge avoids
     * cluttering the tables over time, reducing the space and time complexity
     * of typical/frequent database operations.
     *
     * Ideally native  and immigrant ticks would be purged as well, however the
     * distributed synchronization algorithm does not allow that. Indeed, we
     * cannot safely accept ticks about ourselves from a remote device.
     *
     * In the past we moved ticks around from the regular tick table to a special
     * backup table. However that was costly in terms of CPU and  disk I/O, it
     * increased DB fragmentation and caused long pauses when deleting large
     * stores.
     *
     * The new approach is much simpler:
     *   - native ticks for the local device are preserved in the MAXTICKS table
     *   - immigrant ticks for the local device are preserved in-place
     */

    void deleteTicksAndKnowledgeForStore_(SIndex sidx, Trans t) throws SQLException;

    /**
     * @return all backed-up tick entries
     */
    public @Nonnull IDBIterator<E> getBackupTicks_(SIndex sidx) throws SQLException;
}
