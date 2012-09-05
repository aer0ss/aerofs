package com.aerofs.daemon.core.collector;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Map.Entry;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import com.aerofs.daemon.lib.db.ICollectorFilterDatabase;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CollectorFilters
{
    private static final Logger l = Util.l(CollectorFilters.class);

    private final ICollectorFilterDatabase _cfdb;
    private final TransManager _tm;

    private final SIndex _sidx;

    private static class DeviceEntry
    {
        private final DID _did;

        // it acts as a write-through cache for the db
        private @Nullable BFOID _dbFilter;

        private boolean _dirty;

        // the content must be consistent with _c2d2f
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
    private final SortedMap<CollectorSeq, Map<DID, BFOID>> _c2d2f = Maps.newTreeMap();

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
            if (l.isInfoEnabled()) l.info("save db 4 " + _sidx + " " + did +
                    " " + filterNew);

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
        assert _c2d2f.isEmpty();

        List<DeviceEntry> devs = Lists.newArrayList();
        for (DeviceEntry dev : _did2dev.values()) {
            assert dev._css.isEmpty();
            // skip devices whose filter is already reset
            if (dev.getDBFilter_() != null && !dev._dirty) {
                devs.add(dev);
            }
            dev._dirty = false;
        }

        if (!devs.isEmpty()) {
            Trans t2 = t == null ? _tm.begin_() : t;
            try {
                for (DeviceEntry dev : devs) {
                    dev.setDBFilter_(null);
                    // N.B. if the transaction is rolled back, the in-memory db
                    // filter becomes inconsistent with the db, which is fine
                    l.info("delete cf from db " + _sidx + " " + dev._did);
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
        assert en != null;
        for (CollectorSeq cs : en._css) {
            Map<DID, BFOID> d2f = _c2d2f.get(cs);
            assert d2f.containsKey(did); // although the value may be null
            d2f.remove(did);
            if (d2f.isEmpty()) Util.verify(_c2d2f.remove(cs) == d2f);
        }

        assert !_did2dev.isEmpty() || _c2d2f.isEmpty();
    }

    /**
     * @return null if the filter is not found
     */
    private @Nullable BFOID getCSFilter_(DID did, CollectorSeq cs)
    {
        Map<DID, BFOID> d2f = _c2d2f.get(cs);
        return d2f == null ? null : d2f.get(did);
    }

    /**
     * @return the old filter, or null
     */
    private @Nullable BFOID setCSFilter_(DeviceEntry dev, CollectorSeq cs, @Nonnull BFOID filter)
    {
        l.info("set cs filter for " + _sidx + " " + dev._did + " cs " + cs);
        filter.finalize_();

        dev._css.add(cs);

        Map<DID, BFOID> d2f = _c2d2f.get(cs);
        if (d2f == null) {
            d2f = Maps.newTreeMap();
            _c2d2f.put(cs, d2f);
        }
        return d2f.put(dev._did, filter);
    }

    /**
     * the caller must guarantee the device is loaded
     */
    void addCSFilter_(DID did, CollectorSeq cs, BFOID filter)
    {
        l.info("add cs filter to " + _sidx + " " + did + " cs " + cs);
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
     * the caller must guarantee that 1) the device has been loaded,
     * 2) the method hasn't been called since the last loading of the device, 3)
     * the db filter is not null
     */
    void setCSFilterFromDB_(DID did, CollectorSeq cs)
    {
        DeviceEntry dev = _did2dev.get(did);
        BFOID db = dev.getDBFilter_();
        assert db != null : did + " " + cs;
        Util.verify(setCSFilter_(dev, cs, db) == null);
    }

    /**
     * this method applies the db filter to cs for all the loaded devices. the
     * caller must guarantee that setCSFilterFromDB() hasn't been called on
     * any loaded device
     */
    void setAllCSFiltersFromDB_(CollectorSeq cs)
    {
        for (DeviceEntry dev : _did2dev.values()) {
            BFOID db = dev.getDBFilter_();
            if (db != null) Util.verify(setCSFilter_(dev, cs, db) == null);
        }
    }

    void addAllCSFiltersFromDB_(CollectorSeq cs)
    {
        for (DeviceEntry dev : _did2dev.values()) {
            BFOID db = dev.getDBFilter_();
            if (db != null) addCSFilter_(dev, cs, db);
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
            sub = _c2d2f.headMap(csEnd.plusOne());
        } else if (csEnd == null) {
            sub = _c2d2f.tailMap(csStart);
        } else {
            assert csStart.compareTo(csEnd) <= 0;
            sub = _c2d2f.subMap(csStart, csEnd.plusOne());
        }

        for (Entry<CollectorSeq, Map<DID, BFOID>> en : sub.entrySet()) {
            for (DID did : en.getValue().keySet()) {
                l.info("disposing " + _sidx + " " + en.getKey() + " " + did);
                DeviceEntry dev = _did2dev.get(did);
                Util.verify(dev._css.remove(en.getKey()));
            }
        }

        sub.clear();

        return !_c2d2f.isEmpty();
    }

    void deleteAllCSFilters_()
    {
        _c2d2f.clear();
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
    Set<DID> getDevicesHavingComponent_(OCID ocid)
    {
        int[] indics = BFOID.HASH.hash(ocid.oid());
        Set<DID> ret = Sets.newTreeSet();
        for (Map<DID, BFOID> d2f : _c2d2f.values()) {
            for (Entry<DID, BFOID> en : d2f.entrySet()) {
                DID did = en.getKey();
                if (ret.contains(did)) continue;
                if (!en.getValue().contains_(indics)) continue;
                ret.add(did);
            }
        }
        return ret;
    }

}
