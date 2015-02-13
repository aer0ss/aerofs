/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.device;

import com.aerofs.ids.DID;
import com.aerofs.lib.Util;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.collect.Maps.newTreeMap;

// FIXME (AG): we should simply store this set directly inside a store
// There was an old comment here claiming that we couldn't because the store
// had to differentiate between confirmed and unconfirmed members, but in practice
// no such distinction is made.
/**
 * OPM = online potential member
 * <p/>
 * Represents the set of devices which are <em>potential</em> members for a store
 */
public class OPMDevices
{
    private final Map<DID, Device> _opmd = newTreeMap();

    void add_(DID did, Device dev)
    {
        Util.verify(_opmd.put(did, dev) == null);
    }

    void remove_(DID did)
    {
        Util.verify(_opmd.remove(did) != null);
    }

    boolean isEmpty_()
    {
        return _opmd.isEmpty();
    }

    public ImmutableMap<DID, Device> getAll_()
    {
        return ImmutableMap.copyOf(_opmd);
    }

    @Override
    public String toString()
    {
        return _opmd.toString();
    }
}