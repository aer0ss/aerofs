/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.device;

import com.aerofs.base.id.DID;
import com.aerofs.lib.Util;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.collect.Maps.newTreeMap;

/**
 * OPM = online potential member
 * <p/>
 * Represents the set of devices which are potential members for a store
 * <p/>
 * We can't store OPM devices in a {@link com.aerofs.daemon.core.store.Store},
 * because the transport layer may incorrectly report a device as belonging to a store.
 * Since Store objects should only store confirmed member devices, presence for
 * non-confirmed devices would be lost. This is unacceptable, since a non-member can
 * have its status elevated to that of a member in the future. Ideally, the transport should
 * not report presence for non-member devices. In practice this is hard due to concurrency
 * and implementation details with the transport
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