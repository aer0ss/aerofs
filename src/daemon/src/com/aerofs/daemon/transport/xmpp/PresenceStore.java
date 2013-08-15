/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.transport.lib.IPresenceManager;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Maps.newTreeMap;
import static com.google.common.collect.Sets.newHashSet;

/**
 * Manages the last-known presence for a set of XMPP-available peers
 */
public final class PresenceStore implements IPresenceManager
{
    private Map<DID, Set<SID>> _online = newTreeMap();

    /**
     * Indicate that a did is online for a store
     *
     * @param did DID that came online
     * @param sid store id for which the DID came online
     */
    public synchronized void add(DID did, SID sid)
    {
        checkNotNull(did);
        checkNotNull(sid);

        Set<SID> sids = _online.get(did);
        if (sids == null) sids = new TreeSet<SID>();

        sids.add(sid);
        _online.put(did, sids);
    }

    /**
     * Indicate that a did is offline for a store
     *
     * @param did DID that went offline
     * @param sid store id for which the DID went offline
     * @return whether this was the last store address for the DID
     */
    public synchronized boolean remove(DID did, SID sid)
    {
        checkNotNull(did);
        checkNotNull(sid);

        Set<SID> sids = _online.get(did);
        if (sids == null) return true;

        sids.remove(sid);
        if (sids.isEmpty()) {
            _online.remove(did);
            return true;
        }

        return false;
    }

    /**
     * Indicate whether the did is online.
     *
     * @param did DID to check
     * @return true if the device is online for any store, false otherwise
     */
    public synchronized boolean has(DID did)
    {
        return _online.containsKey(did);
    }

    @Override
    public boolean isPresent(DID did)
    {
        return has(did);
    }

    public synchronized Set<DID> availablePeers()
    {
        return newHashSet(_online.keySet());
    }
}
