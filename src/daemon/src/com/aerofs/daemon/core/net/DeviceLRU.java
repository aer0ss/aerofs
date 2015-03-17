package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.IDeviceEvictionListener;
import com.aerofs.daemon.lib.LRUCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cache of DIDs used by the transport, with Least Recently Used eviction policy
 *
 * This class is thread-safe and eviction listeners added to it MUST also be thread-safe.
 */
public class DeviceLRU
{
    private static final Logger l = Loggers.getLogger(DeviceLRU.class);

    private final LRUCache<DID, Object> _devices;
    private final List<IDeviceEvictionListener> _listeners =
            Collections.synchronizedList(Lists.newLinkedList());

    public void addEvictionListener(IDeviceEvictionListener l)
    {
        _listeners.add(l);
    }

    public DeviceLRU(int numDevices)
    {
        _devices = new LRUCache<>(true, numDevices, (d, o) -> {
            l.debug("evict d:{}", d);
            for (IDeviceEvictionListener l : _listeners) l.evicted(d);
        });
    }

    public synchronized void addDevice(DID d)
    {
        _devices.put_(d, null);
    }
}
