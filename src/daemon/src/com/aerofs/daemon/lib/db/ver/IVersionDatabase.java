package com.aerofs.daemon.lib.db.ver;

import java.sql.SQLException;
import java.util.Set;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Tick;
import com.aerofs.lib.Version;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;

import javax.annotation.Nonnull;

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

    void deleteTicksAndKnowledgeForStore_(SIndex sidx, Trans t) throws SQLException;


    /* ======================= BACKUP TICKS ==================================
     *
`    * Need to restore the exact local tick history of a device on a resync.
     * These methods provide the means to backup and retrieve local ticks to
     * a different table than the version table.
     * The DID parameter is not necessary as it is implicitly the local DID.
     * For more details, see below.
     */

    /**
     * Backup all *TickRows of the iterator into a different table than used for
     * regular versions
     * @param sidx the Store Index associated with the tick row
     */
    public void insertBackupTicks_(SIndex sidx, IDBIterator<E> iter, Trans t)
            throws SQLException;

    /**
     * @return all backed-up tick entries in ascending order
     */
    public @Nonnull IDBIterator<E> getBackupTicks_(SIndex sidx) throws SQLException;

    public void deleteBackupTicksFromStore_(SIndex sidx, Trans t) throws SQLException;


    /*
     * On Backing up Local Device Ticks During Store Deletion
     * -------------------------------------------------------------------------
     * When a store is locally deleted, all data related to the store is deleted
     * from DB tables that are used in typical operations. This purge avoids
     * cluttering the tables over time, reducing the space and time complexity
     * of typical/frequent database operations. The Native and Immigrant Version
     * tables are included in the purge, but for correctness the tick history of
     * the local machine must be restorable.
     *
     * This interface concerns the backup of Native Version ticks. Recall that
     * in the Anti-Entropy pull algorithm, the local device will query a remote
     * device for knowledge that exceeds its own: all ticks below the remote
     * knowledge vector that exceed the local knowledge vector are downloaded.
     * Following this exchange the local device can safely increase its own
     * knowledge vector to match the remote's. Now consider what would happen if
     * the remote device had deleted all ticks about itself for the store of
     * interest. Its knowledge about the store is now zero. Obviously if the
     * store remains deleted on that device, it has no files/folders to share,
     * so the lack of knowledge to pull is acceptable. Furthermore, the remote
     * device will not create any new ticks (as the store is deleted, and
     * modifications cannot be made), so again, the device's knowledge is no
     * longer of interest to other devices.
     *
     * On resync or restoration of the store, the resynced device's knowledge
     * about its own history becomes necessary. Consider the contrary; suppose
     * we did not restore the device's own tick history, and the resynced device
     * has zero knowledge of its prior object modifications prior to store
     * deletion. The device could acquire some knowledge about its history from
     * other devices by pulling via Anti-Entropy; it could even acquire the
     * entire knowledge of its former history if some device is online that was
     * entirely consistent with the remote before it deleted the store. However,
     * consider that if no such device exists online, the resynced device will
     * never reach its former knowledge level. When the device makes local
     * modifications after the resync, it will create new ticks, but because it
     * never reached its former knowledge level, the device cannot increase its
     * knowledge of itself up to the newly created ticks, as this would break
     * the invariant that all ticks below the knowledge vector follow a globally
     * unique history. Therefore on AE-pulls by other devices, the resynced
     * device cannot share its new object modifications as "knowledge," and
     * therefore none of its new modifications after the resync will be shared
     * as knowledge with the rest of the system of devices.
     *
     * From this semi proof-by-contradiction, it is apparent that we need to
     * restore the exact local tick history of a device on a resync, and
     * therefore this interface provides the means to backup and retrieve local
     * ticks. The DID parameter is not necessary as it is implicitly the local
     * DID.
     *
     * @author markj
     */
}
