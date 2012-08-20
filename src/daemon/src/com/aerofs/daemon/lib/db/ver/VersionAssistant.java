package com.aerofs.daemon.lib.db.ver;

import java.sql.SQLException;
import java.util.Set;

import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

/**
 * This class maintains the consistency between various version tables, notably
 * the IV and MAX_TICKS tables. The clients only need to write the V table, and
 * this class will update other tables automatically to reflect the change.
 */
public class VersionAssistant extends AbstractTransListener
{
    private boolean _committed;   // for debugging

    private final Set<SOCID> _changed = Sets.newHashSet();
    private final Set<SOID> _addedLocal = Sets.newHashSet();
    private final Set<SOCID> _deletedPermanently = Sets.newHashSet();
    private final Set<SIndex> _deletedStores = Sets.newHashSet();

    private final INativeVersionDatabase _nvdb;
    private final IImmigrantVersionDatabase _ivdb;
    private final MapSIndex2Store _sidx2s;

    public static class Factory
    {
        private final INativeVersionDatabase _nvdb;
        private final IImmigrantVersionDatabase _ivdb;
        private final MapSIndex2Store _sidx2s;

        @Inject
        public Factory(INativeVersionDatabase nvdb,
                IImmigrantVersionDatabase ivdb,
                MapSIndex2Store sidx2s)
        {
            _sidx2s = sidx2s;
            _nvdb = nvdb;
            _ivdb = ivdb;
        }

        public VersionAssistant create_()
        {
            return new VersionAssistant(_sidx2s, _nvdb, _ivdb);
        }
    }

    /**
     * this method should be called only by the Trans class
     */
    private VersionAssistant(MapSIndex2Store sidx2s, INativeVersionDatabase nvdb,
            IImmigrantVersionDatabase ivdb)
    {
        _sidx2s = sidx2s;
        _nvdb = nvdb;
        _ivdb = ivdb;
    }

    public void kmlVersionAdded_(SOCID socid, Version v)
    {
        assert !_committed;
        _changed.add(socid);
    }

    public void localVersionAdded_(SOCID socid, Version v)
    {
        assert !_committed;
        _changed.add(socid);
        _addedLocal.add(socid.soid());
    }

    /**
     * call this method when deleting a version to add it back to another branch
     * or as KML later
     */
    public void versionDeleted_(SOCID socid, Version v)
    {
        assert !_committed;
        _changed.add(socid);
    }

    /**
     * For debugging only: stores should not be deleted in same transaction
     * as versions are modified
     */
    public void storeDeleted_(SIndex sidx)
    {
        assert !_committed;
        _deletedStores.add(sidx);
    }

    /**
     * call this method when deleting a version permanently from the component
     * without adding back to the same component, e.g. during aliasing
     */
    public void versionDeletedPermanently_(SOCID socid, Version v)
    {
        assert !_committed;
        _deletedPermanently.add(socid);
        _changed.add(socid);
    }

    /**
     * Write the version changes into the database
     */
    @Override
    public void committing_(Trans t) throws SQLException
    {
        assert !_committed;
        _committed = true;

        // When versions are permanently deleted, their corresponding max ticks should be deleted,
        // too. Because we update max ticks only for versions in the _changed set,
        // _deletedPermanently must be a subset of _changed.
        assert _changed.containsAll(_deletedPermanently);
        for (SOCID socid : _deletedPermanently) {
            assertSidxNotContainedInDeletedStores(socid.sidx());
            _nvdb.deleteMaxTicks_(socid, t);
            _ivdb.deleteImmigrantTicks_(socid, t);
        }

        for (SOCID socid : _changed) {
            assertSidxNotContainedInDeletedStores(socid.sidx());
            _nvdb.updateMaxTicks_(socid, t);
        }

        for (SOID soid : _addedLocal) {
            assertSidxNotContainedInDeletedStores(soid.sidx());
            // s shouldn't disappear within the transaction where the soid is added
            Store s = _sidx2s.get_(soid.sidx());
            s.senderFilters().objectUpdated_(soid.oid(), t);
        }
    }

    /**
     * Due to complexity of deleting vs. backing up versions, no version
     * should be modified/added/deleted in the same transaction as its
     * container store is deleted.
     * Specifically, if a version is updated *after* its store is deleted
     * the version will not be deleted from the correct table, and it will
     * correspondingly not be backed up.  This would leave the DB in a somewhat
     * inconsistent state.
     * @param sidx Store index of an object whose version was updated
     */
    private void assertSidxNotContainedInDeletedStores(SIndex sidx)
    {
        assert !(_deletedStores.contains(sidx));
    }
}
