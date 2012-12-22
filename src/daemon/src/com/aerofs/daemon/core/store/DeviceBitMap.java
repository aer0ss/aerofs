/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.store;

import com.aerofs.base.id.DID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * To reduce storage overhead, sync status is stored as a bitvector in the object attribute table.
 * Each bit in that vector corresponds to a device the object in question is shared with (either a
 * device from the same user or from a different user in the case of shared folders). The mapping of
 * a bitvector index to a device ID is done at store granularity.
 *
 * This class provides a fast in-memory bidirectional mapping between DID and bitvector position.
 * It is not meant to be created manually. Instances of it can be obtained from
 * {@link MapSIndex2DeviceBitMap}
 */
public class DeviceBitMap implements Iterable<DID>
{
    private List<DID> _l;
    private Map<DID, Integer> _m;

    /**
     * Only available for testing purposes, do NOT use in the daemon
     */
    public DeviceBitMap(DID... dids)
    {
        _l = Lists.newArrayList();
        _m = Maps.newHashMap();

        for (DID did : dids) {
            _m.put(did, _l.size());
            _l.add(did);
        }
    }

    /**
     * Create and fill map from concatenated DIDs
     * NOTE: only MapSIndex2DeviceBitMap should use this ctor
     * @param dids byte array of concatenated DIDs
     */
    DeviceBitMap(@Nullable byte[] dids)
    {
        _l = Lists.newArrayList();
        _m = Maps.newHashMap();

        if (dids != null) {
            assert dids.length % DID.LENGTH == 0;
            for (int i = 0; i + DID.LENGTH <= dids.length; i += DID.LENGTH) {
                DID did = new DID(Arrays.copyOfRange(dids, i, i + DID.LENGTH));
                _m.put(did, _l.size());
                _l.add(did);
            }
        }
    }

    /**
     * Add DID to the map
     *
     * NOTE: only MapSIndex2DeviceBitMap should use this method, it is only public for testing
     * purposes
     *
     * @pre the DID must not be present in the map
     * @return index of the DID in the map
     */
    public int addDevice_(DID did)
    {
        assert get(did) == null;
        int idx = _l.size();
        _m.put(did, idx);
        _l.add(did);
        return idx;
    }

    /**
     * NOTE: only MapSIndex2DeviceBitMap should use this method
     * @return Concatenated byte array representation suitable for DB storage
     */
    byte[] getBytes()
    {
        byte[] d = new byte[_l.size() * DID.LENGTH];
        int i = 0;
        for (DID did : _l) {
            System.arraycopy(did.getBytes(), 0, d, i++ * DID.LENGTH, DID.LENGTH);
        }
        return d;
    }

    /**
     * @return number of devices in the mapping
     */
    public int size()
    {
        return _l.size();
    }

    /**
     * @return bitvector index corresponding to a given DID, or null if the DID is not present in
     * the mapping
     */
    public @Nullable Integer get(DID did)
    {
        return _m.get(did);
    }

    /**
     * @pre index is present in the mapping
     * @return DID corresponding to a given bitvector index
     * @throws IndexOutOfBoundsException if the index does not correspond to a valid DID
     */
    public DID get(int index)
    {
        return _l.get(index);
    }

    /**
     * @return Iterator on all the DIDs in the mapping (ordered by bitvector index)
     */
    public Iterator<DID> iterator()
    {
        return _l.iterator();
    }
}
