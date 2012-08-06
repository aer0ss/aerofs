/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Manages the last-known presence for a set of XMPPBasedTransportFactory-available peers
 */
final class XMPPPresenceStore
{
    /**
     * Indicate that a <code>did</code> is online for a store
     *
     * @param did <code>DID</code> that came online
     * @param sid store id for which the DID came online
     * @return <code>true</code> if this is the first entry for this peer, <code>false</code>
     *         otherwise
     */
    public synchronized boolean add(DID did, SID sid)
    {
        assert did != null;
        assert sid != null;

        boolean wasfirst = false;
        Set<SID> sids = _online.get(did);
        if (sids == null) {
            sids = new TreeSet<SID>();
            wasfirst = true;
        }

        sids.add(sid);
        _online.put(did, sids);

        return wasfirst;
    }

    /**
     * Indicate that a <code>did</code> is offline for a store
     *
     * @param did <code>DID</code> that went offline
     * @param sid store id for which the DID went offline
     * @return whether this was the last store address for the DID; true if this entry was deleted,
     *         false otherwise
     */
    public synchronized boolean del(DID did, SID sid)
    {
        assert did != null;
        assert sid != null;

        Set<SID> sids = _online.get(did);
        if (sids == null) return true;

        boolean waslast = false;
        sids.remove(sid);
        if (sids.isEmpty()) {
            _online.remove(did);
            waslast = true;
        }

        return waslast;
    }

    /**
     * Indicate whether the <code>did</code> is online for <em>any</em> store and (as a result) is
     * online
     *
     * @param did <code>DID</code> to check
     * @return true if the device is online for any store, false otherwise
     */
    public synchronized boolean has(DID did)
    {
        return _online.containsKey(did);
    }

    /**
     * Indicate whether the <code>did</code> is online for a <em>specific</em> store
     *
     * @param did <code>DID</code> to check
     * @param sid store id for which we should see if the device is online
     * @return true if the device is online for any store, false otherwise
     */
    public synchronized boolean has(DID did, SID sid)
    {
        if (!has(did)) return false;

        Set<SID> sids = _online.get(did);
        return sids.contains(sid);
    }

    /**
     * Delete all presence entries
     */
    public synchronized void delall()
    {
        _online.clear();
    }

    private Map<DID, Set<SID>> _online = new TreeMap<DID, Set<SID>>();
}
