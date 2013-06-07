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

    private final Factory _f;

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
            return new VersionAssistant(this);
        }
    }

    /**
     * this method should be called only by the Trans class
     */
    private VersionAssistant(Factory f)
    {
        _f = f;
    }

    public void kmlVersionAdded_(SOCID socid)
    {
        assert !_committed;
        _changed.add(socid);
    }

    public void localVersionAdded_(SOCID socid)
    {
        assert !_committed;
        // There's a safe ordering and an unsafe ordering here.
        // It's safe to update an object in a store, then delete the store,
        // but it's not safe to delete the store and then add an object under it.
        // This is why we make this assertion early instead of at commit time.
        assertSidxNotContainedInDeletedStores(socid.sidx());
        _changed.add(socid);
        _addedLocal.add(socid.soid());
    }

    /**
     * call this method when deleting a version to add it back to another branch
     * or as KML later
     */
    public void versionDeleted_(SOCID socid)
    {
        assert !_committed;
        _changed.add(socid);
    }

    /**
     * For debugging only: stores should not be deleted in the same transaction
     * as versions on objects within that store are modified
     */
    public void storeDeleted_(SIndex sidx)
    {
        assert !_committed;
        _deletedStores.add(sidx);
        // TODO: cleanup contributors? would require restoring
    }

    /**
     * call this method when deleting a version permanently from the component
     * without adding back to the same component, e.g. during aliasing
     */
    public void versionDeletedPermanently_(SOCID socid)
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
            assertSocidVersionIsZeroOrNotUnderDeletedStore(socid);
            _f._nvdb.deleteMaxTicks_(socid, t);
            _f._ivdb.deleteImmigrantTicks_(socid, t);
        }

        for (SOCID socid : _changed) {
            assertSocidVersionIsZeroOrNotUnderDeletedStore(socid);
            _f._nvdb.updateMaxTicks_(socid, t);
        }

        for (SOID soid : _addedLocal) {
            // It's possible that we added a new object locally, then went and deleted the
            // containing store.  For such stores, we needn't dispatch objectUpdated events on
            // commit, since we no longer track that store anyway.
            // There's an assertion above to make sure the named events happen in the safe order.
            Store s = _f._sidx2s.getNullable_(soid.sidx());
            if (s != null) {
                s.senderFilters().objectUpdated_(soid.oid(), t);
            }
        }
    }

    /**
     * Due to complexity of deleting vs. backing up versions, no version
     * should be modified/added/deleted after its container store is deleted.
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

    /**
     * It's possible to update a version in the same transaction as its
     * object's store is deleted.  We just need to make sure that the object's
     * version was deleted correctly.
     */
    private void assertSocidVersionIsZeroOrNotUnderDeletedStore(SOCID socid)
            throws SQLException
    {
        if (_deletedStores.contains(socid.sidx())) {
            Version v = _f._nvdb.getAllVersions_(socid);
            assert v.isZero_() : socid + " " + v;
        }
    }
}
