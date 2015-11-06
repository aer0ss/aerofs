package com.aerofs.daemon.core.collector;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Map.Entry;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import com.aerofs.daemon.lib.db.ICollectorFilterDatabase;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

public class CollectorFilters
{
    private static final Logger l = Loggers.getLogger(CollectorFilters.class);

    private final ICollectorFilterDatabase _cfdb;
    private final TransManager _tm;

    private final SIndex _sidx;

    private static class DeviceEntry
    {
        private final DID _did;

        // it acts as a write-through cache for the db
        private @Nullable BFOID _dbFilter;

        private boolean _dirty;

        // the content must be consistent with _cs2did2bf
        final Set<CollectorSeq> _css = Sets.newTreeSet();

        DeviceEntry(DID did)
        {
            _did = did;
        }

        /**
         * @param filter finalized after the method call
         */
        void setDBFilter_(@Nullable BFOID filter)
        {
            if (filter != null) filter.finalize_();
            _dbFilter = filter;
        }

        @Nullable BFOID getDBFilter_()
        {
            return _dbFilter;
        }
    }

    // it contains all the loaded devices. even though they may have no filters
    private final Map<DID, DeviceEntry> _did2dev = Maps.newTreeMap();

    // all the devices included in this map must have been loaded.
    private final SortedMap<CollectorSeq, Map<DID, BFOID>> _cs2did2bf = Maps.newTreeMap();

    CollectorFilters(ICollectorFilterDatabase csdb, TransManager tm, SIndex sidx)
    {
        _tm = tm;
        _cfdb = csdb;
        _sidx = sidx;
    }

    /**
     * the method can be called regardless of if the device is loaded or unloaded
     *
     * @param filter finalized after the method call
     * @return true if the device has been loaded
     */
    boolean addDBFilter_(final DID did, BFOID filter, Trans t) throws SQLException
    {
        assert !filter.isEmpty_();
        filter.finalize_();

        final DeviceEntry dev = _did2dev.get(did);

        BFOID filterOld;
        BFOID filterNew;
        boolean changed;
        if (dev != null) {
            filterOld = dev.getDBFilter_();
            if (filterOld == null) {
                filterNew = filter;
                changed = true;
            } else {
                filterNew = new BFOID(filterOld);
                changed = filterNew.union_(filter);
            }
        } else {
            filterOld = _cfdb.getCollectorFilter_(_sidx, did);
            if (filterOld == null) {
                filterNew = filter;
                changed = true;
            } else {
                filterNew = filterOld;
                changed = filterNew.union_(filter);
            }
        }

        if (changed) {
            if (l.isDebugEnabled()) l.debug("save db 4 " + _sidx + " " + did + " " + filterNew);

            _cfdb.setCollectorFilter_(_sidx, did, filterNew, t);

            if (dev != null) {
                dev.setDBFilter_(filterNew);

                final BFOID filterOldFinal = filterOld;
                t.addListener_(new AbstractTransListener() {
                    @Override
                    public void aborted_()
                    {
                        // must revert the in-memory cache to the previous state
                        // on rollback otherwise the next time the filter is
                        // re-attempted we wouldn't added it because the changed
                        // flag (see above) would be false.
                        dev.setDBFilter_(filterOldFinal);
                    }
                });
            }
        }

        return dev != null;
    }

    /**
     * remove the db filters of non-dirty devices from the db. the caller may
     * ignore the exception as it's not a big deal.
     */
    void cleanUpDBFilters_(@Nullable Trans t) throws SQLException
    {
        // before the collector stops it must clear all the filters (to avoid
        // them being reused by the next collection iteration)
        assert _cs2did2bf.isEmpty();

        List<DeviceEntry> toDelete = Lists.newArrayList();
        for (DeviceEntry dev : _did2dev.values()) {
            assert dev._css.isEmpty();
            // skip devices whose filter is already reset
            if (dev.getDBFilter_() != null && !dev._dirty) {
                toDelete.add(dev);
            }
            dev._dirty = false;
        }

        if (!toDelete.isEmpty()) {
            Trans t2 = t == null ? _tm.begin_() : t;
            try {
                for (DeviceEntry dev : toDelete) {
                    dev.setDBFilter_(null);
                    // N.B. if the transaction is rolled back, the in-memory db
                    // filter becomes inconsistent with the db, which is fine
                    l.debug("delete cf from db " + _sidx + " " + dev._did);
                    _cfdb.deleteCollectorFilter_(_sidx, dev._did, t);
                }
                if (t == null) t2.commit_();
            } finally {
                if (t == null) t2.end_();
            }
        }
    }

    /**
     * the caller must guarantee the device is not loaded
     * @return true if the db filter of the device is not empty
     */
    boolean loadDBFilter_(DID did) throws SQLException
    {
        BFOID filterDB = _cfdb.getCollectorFilter_(_sidx, did);
        DeviceEntry dev = new DeviceEntry(did);
        Util.verify(_did2dev.put(did, dev) == null);
        dev.setDBFilter_(filterDB);
        return filterDB != null;
    }

    /**
     * the caller must guarantee the device is loaded
     */
    void unloadAllFilters_(DID did)
    {
        DeviceEntry en = _did2dev.remove(did);
        if (en == null) return;
        for (CollectorSeq cs : en._css) {
            Map<DID, BFOID> d2f = _cs2did2bf.get(cs);
            assert d2f.containsKey(did); // although the value may be null
            d2f.remove(did);
            if (d2f.isEmpty()) Util.verify(_cs2did2bf.remove(cs) == d2f);
        }

        assert !_did2dev.isEmpty() || _cs2did2bf.isEmpty();
    }

    /**
     * @return null if the filter is not found
     */
    private @Nullable BFOID getCSFilter_(DID did, CollectorSeq cs)
    {
        Map<DID, BFOID> d2f = _cs2did2bf.get(cs);
        return d2f == null ? null : d2f.get(did);
    }

    /**
     * @return the old filter, or null
     */
    private @Nullable BFOID setCSFilter_(DeviceEntry dev, CollectorSeq cs, @Nonnull BFOID filter)
    {
        l.debug("set cs filter for " + _sidx + " " + dev._did + " cs " + cs);
        filter.finalize_();

        dev._css.add(cs);

        Map<DID, BFOID> d2f = _cs2did2bf.get(cs);
        if (d2f == null) {
            // TODO: prevent explosive growth of _cs2didbf by merging sparse BFs
            // see getDevicesHavingComponent_ for a rationale
            d2f = Maps.newTreeMap();
            _cs2did2bf.put(cs, d2f);
        }
        return d2f.put(dev._did, filter);
    }

    /**
     * the caller must guarantee the device is loaded
     */
    void addCSFilter_(DID did, CollectorSeq cs, BFOID filter)
    {
        l.debug("add cs filter to " + _sidx + " " + did + " cs " + cs);
        addCSFilter_(_did2dev.get(did), cs, filter);
    }

    private void addCSFilter_(DeviceEntry dev, CollectorSeq cs, @Nonnull BFOID filter)
    {
        filter.finalize_();

        BFOID filterOld = getCSFilter_(dev._did, cs);
        BFOID filterNew;
        if (filterOld == null) {
            filterNew = filter;
        } else {
            filterNew = new BFOID(filterOld);
            if (!filterNew.union_(filter)) return;
        }

        Util.verify(setCSFilter_(dev, cs, filterNew) == filterOld);
    }

    /**
     * the caller must guarantee that
     * 1) the given CS is the bottom of the collector queue
     * 2) the device has been loaded
     * 3) the db filter is not null
     * 4) no BF for this device has been attached to any CS since the device was loaded
     */
    void setCSFilterFromDB_(DID did, CollectorSeq cs)
    {
        DeviceEntry dev = _did2dev.get(did);
        BFOID db = dev.getDBFilter_();
        assert db != null : did + " " + cs;
        // make sure no BF attached to any CS for this device as they would be redundant
        assert dev._css.isEmpty() : did + " " + dev._css;
        Util.verify(setCSFilter_(dev, cs, db) == null);
    }

    /**
     * apply the db filter to cs for all the loaded devices. the caller must guarantee that
     * setCSFilterFromDB() hasn't been called on any loaded device.
     */
    void setAllCSFiltersFromDB_(CollectorSeq cs)
    {
        for (DeviceEntry dev : _did2dev.values()) {
            BFOID db = dev.getDBFilter_();
            if (db != null) Util.verify(setCSFilter_(dev, cs, db) == null);
        }
    }

    /**
     * remove all the filters in the range of [csStart, csEnd],
     * (-infinity, csEnd], or [csStart, +infinity), depending on if csStart
     * or csEnd is null. They can't be both null at the same time.
     * @param csStart must be less than csEnd.
     * @param csEnd must be greater than csStart.
     * @return false if there is no more cs filters left
     */
    boolean deleteCSFilters_(@Nullable CollectorSeq csStart, @Nullable CollectorSeq csEnd)
    {
        assert csStart != null || csEnd != null;

        Map<CollectorSeq, Map<DID, BFOID>> sub;
        if (csStart == null) {
            sub = _cs2did2bf.headMap(csEnd.plusOne());
        } else if (csEnd == null) {
            sub = _cs2did2bf.tailMap(csStart);
        } else {
            checkArgument(csStart.compareTo(csEnd) <= 0, "%s > %s", csStart, csEnd);
            sub = _cs2did2bf.subMap(csStart, csEnd.plusOne());
        }

        for (Entry<CollectorSeq, Map<DID, BFOID>> en : sub.entrySet()) {
            for (DID did : en.getValue().keySet()) {
                l.debug("disposing " + _sidx + " " + en.getKey() + " " + did);
                DeviceEntry dev = _did2dev.get(did);
                Util.verify(dev._css.remove(en.getKey()));
            }
        }

        sub.clear();

        return !_cs2did2bf.isEmpty();
    }

    void deleteAllCSFilters_()
    {
        _cs2did2bf.clear();
        for (DeviceEntry dev : _did2dev.values()) dev._css.clear();
    }

    /**
     * can be called regardless of whether the device is loaded or not
     */
    void setDirtyBit_(DID did)
    {
        DeviceEntry dev = _did2dev.get(did);
        if (dev != null) dev._dirty = true;
    }

    /**
     * @return the set of devices whose filters contain the component
     */
    Set<DID> getDevicesHavingComponent_(OID oid)
    {
        int[] indics = BFOID.HASH.hash(oid);
        Set<DID> ret = Sets.newTreeSet();

        // iterating over _did2dev instead of _cs2didbf has a few advantages:
        // 1) we can leverage the db filter, if available to do a first rough elimination
        // 2) we can use early termination of the _cs2didbf iteration
        // 3) for all devices that are hits on the db filter we only need to iterate over a
        // subset of _cs2didbf
        // 4) we don't need to repeatedly test for membership in the result set
        //
        // the only disadvantage is that if all devices have BF attached to the exact same set
        // of CS the iteration overhead will be slightly larger. Fortunately:
        // 1) the most likely case for this to happen is when filters are set from db and in this
        // case there's only one CS involved
        // 2) the number of loaded devices is unlikely to grow faster than the number of updates
        // received from all devices
        for (Entry<DID, DeviceEntry> d : _did2dev.entrySet()) {
            DeviceEntry dev = d.getValue();
            // if this device has many filters scattered at different CS it's worth doing a first
            // rough check via the db filter, if available
            // on the other hand if the device has few filters the cost of the extra test might
            // not be worth the probability of avoiding the other tests...
            if (dev._css.size() > 1) {
                BFOID bf = dev._dbFilter;
                if (bf != null && !bf.contains_(indics)) continue;
            }
            DID did = d.getKey();
            for (CollectorSeq cs : dev._css) {
                if (_cs2did2bf.get(cs).get(did).contains_(indics)) {
                    ret.add(did);
                    break;
                }
            }
        }

        return ret;
    }

    void deletePersistentData_(Trans t)
            throws SQLException
    {
        // TODO (MJ) should I be calling deleteAllCSFilters_() to ensure proper termination
        // of the collector object?
        _cfdb.deleteCollectorFiltersForStore_(_sidx, t);
    }

}
