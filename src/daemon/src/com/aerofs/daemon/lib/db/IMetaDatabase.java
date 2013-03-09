package com.aerofs.daemon.lib.db;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.BitVector;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.CounterVector;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.KIndex;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;

// TODO return iterators instead of collections for all methods

/**
 * This class maintains persistent storage for object metadata including object attributes and
 * content attributes. When possible, use the DirectoryService class which provides a high-level
 * wrapper for this interface.
 */
public interface IMetaDatabase
{
    /**
     * @return null if not found
     */
    @Nullable OID getChild_(SIndex sidx, OID parent, String name) throws SQLException;

    /**
     * the implementation must skip the root folder (e.g. OID.ROOT) and aliased
     * objects
     */
    Set<OID> getChildren_(SOID parent) throws SQLException;

    /**
     * @param flags one or more of ObjectAttr.FLAG_*
     */
    void insertOA_(SIndex sidx, OID oid, OID oidParent, String name, OA.Type type, int flags,
            Trans t) throws ExAlreadyExist, SQLException;

    /**
     * @return the index of all the stores that contain the given OID except {@code sidxExcluded}.
     */
    Collection<SIndex> getSIndexes_(OID oid, SIndex sidxExcluded) throws SQLException;

    /**
     * @return null if not found
     */
    @Nullable OA getOA_(SOID soid) throws SQLException;

    void setOAParentAndName_(SIndex sidx, OID oid, OID parent, String name, Trans t)
            throws SQLException, ExAlreadyExist;

    void setOAParent_(SIndex sidx, OID oid, OID parent, Trans t)
            throws SQLException, ExAlreadyExist;

    // WARNING: use with care lest you shoot yourself in the foot
    void replaceOAOID_(SIndex sidx, OID oidOld, OID oidNew, Trans t)
            throws SQLException, ExAlreadyExist;

    // WARNING: use with care lest you shoot yourself in the foot
    void replaceParentInChildren_(SIndex sidx, OID oidOld, OID oidNew, Trans t)
            throws SQLException, ExAlreadyExist;

    // WARNING: use with care lest you shoot yourself in the foot
    void replaceCA_(SIndex sidx, OID oidOld, OID oidNew, Trans t)
            throws SQLException;

    void setOAFlags_(SOID soid, int flags, Trans t) throws SQLException;

    void setFID_(SOID soid, @Nullable FID fid, Trans t) throws SQLException;

    @Nullable SOID getSOID_(FID fid) throws SQLException;

    void deleteOA_(SIndex sidx, OID alias, Trans t) throws SQLException;

    void insertCA_(SOID soid, KIndex kidx, Trans t) throws SQLException;

    void deleteCA_(SOID soid, KIndex kidx, Trans t) throws SQLException;

    void setCA_(SOID soid, KIndex kidx, long len, long mtime, ContentHash h, Trans t)
            throws SQLException;

    void deleteOAsAndCAsForStore_(SIndex sidx, Trans t) throws SQLException;

    void setCAHash_(SOID soid, KIndex kidx, @Nullable ContentHash h, Trans t) throws SQLException;

    @Nullable ContentHash getCAHash_(SOID soid, KIndex kidx) throws SQLException;

    /**
     * @param sidx may be always null or always not null during a lifecycle of the daemon process.
     */
    long getUsedSpace_(@Nullable SIndex sidx) throws SQLException;

    IDBIterator<SOKID> getAllNonMasterBranches_() throws SQLException;

    /**
     * Retrieve the sync status for an object
     */
    @Nonnull BitVector getSyncStatus_(SOID soid) throws SQLException;

    /**
     * Set the sync status for an object
     */
    void setSyncStatus_(SOID soid, BitVector status, Trans t) throws SQLException;

    /**
     * Retrieve the aggregate sync status for an object
     */
    @Nonnull CounterVector getAggregateSyncStatus_(SOID soid) throws SQLException;

    /**
     * Set the aggregate sync status for an object
     */
    void setAggregateSyncStatus_(SOID soid, CounterVector aggregateStatus, Trans t)
            throws SQLException;

    int getSyncableChildCount_(SOID soid) throws SQLException;
}
