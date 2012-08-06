/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Manages the last-known presence for a set of XMPP-available peers
 */
public class XMPPPresenceManager
{
    /**
     * Indicate that a <code>did</code> is online for a store
     *
     * @param did <code>DID</code> that came online
     * @param sid store id for which the DID came online
     */
    public synchronized void add(DID did, SID sid)
    {
        assert did != null;
        assert sid != null;

        Set<SID> sids = _online.get(did);
        if (sids == null) sids = new TreeSet<SID>();

        sids.add(sid);
        _online.put(did, sids);
    }

    /**
     * Indicate that a <code>did</code> is offline for a store
     *
     * @param did <code>DID</code> that went offline
     * @param sid store id for which the DID went offline
     * @return whether this was the last store address for the DID; true if this
     * entry was deleted, false otherwise
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
     * Indicate whether the <code>did</code> is online for <em>any</em> store
     * and (as a result) is online
     *
     * @param did <code>DID</code> to check
     * @return true if the device is online for any store, false otherwise
     */
    public synchronized boolean has(DID did)
    {
        return _online.containsKey(did);
    }

    /**
     * Indicate whether the <code>did</code> is online for a <em>specific</em>
     * store
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

    private Map<DID, Set<SID>> _online = new TreeMap<DID, Set<SID>>();
}
