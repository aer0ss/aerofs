package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.IDeviceEvictionListener;
import com.aerofs.daemon.lib.LRUCache;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class DeviceLRU
{
    @Nonnull
    private Logger l;
    @Nonnull
    private Set<IDeviceEvictionListener> listeners_;
    @Nonnull
    private LRUCache<DID, Object> devices_;

    public void addEvictionListener_(IDeviceEvictionListener l)
    {
        listeners_.add(l);
    }

    public void removeEvictionListener_(IDeviceEvictionListener l)
    {
        listeners_.remove(l);
    }

    public DeviceLRU(int numDevices)
    {
        l = Loggers.getLogger(DeviceLRU.class);
        listeners_ = new HashSet<>();

        LRUCache.IEvictionListener<DID, Object> el = (d, o) -> {
            l.debug("evict d:" + d);

            for (IDeviceEvictionListener l : listeners_) l.evicted_(d);
        };

        devices_ = new LRUCache<>(true, numDevices, el);
    }

    public void addDevice_(DID d)
    {
        devices_.put_(d, null);
    }
}
