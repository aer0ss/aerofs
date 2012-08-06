package com.aerofs.daemon.core.device;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;

/**
 * OPM = online potential member
 * we can't store OPM devices in the Store object, because the transport layer
 * may report device presence for non-member stores. Store objects only
 * represent member stores, so presence for non-member stores would get lost,
 * which is not acceptable given that a non-member store can become a member in
 * the future. Ideally, the transport should not report presence for non-member
 * stores. In practice it's hard to guarantee due to concurrency of the system
 * and transport implementations.
 */
public class OPMStore
{
    private final Map<DID, Device> _opmd = new TreeMap<DID, Device>();

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

    public Map<DID, Device> getAll_()
    {
        return Collections.unmodifiableMap(_opmd);
    }
}
