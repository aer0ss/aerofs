package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.ids.DID;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class To
{
    // we randomize the order of devices with the same utility value
    private static class DevEntry implements Comparable<DevEntry>
    {
        private final int _utility;
        private final DID _did;
        private final double _rand;

        DevEntry(DID did, int utility)
        {
            _did = did;
            _utility = utility;
            _rand = Math.random();
        }

        @Override
        public int compareTo(DevEntry arg0)
        {
            int comp = _utility - arg0._utility;
            if (comp != 0) return comp;
            comp = Double.compare(_rand, arg0._rand);
            if (comp != 0) return comp;
            return _did.compareTo(arg0._did);
        }
    }

    private final Set<DID> _dids = new HashSet<>();
    private final Set<DID> _avoid = new HashSet<>();

    public static class Factory
    {
        private final Devices __devices;
        private final CfgLocalDID _cfgLocalDID;

        @Inject
        public Factory(Devices devices, CfgLocalDID cfgLocalDID)
        {
            __devices = devices;
            _cfgLocalDID = cfgLocalDID;
        }

        public To create_(DID did)
        {
            return new To(this, did);
        }

        public To create_(Collection<DID> dids)
        {
            return new To(this, dids);
        }
    }

    private final Factory _f;

    /**
     * add a device and permanently disable maxcast
     */
    private To(Factory f, DID did)
    {
        _f = f;

        add_(did);
    }

    /**
     * add a list of devices and permanently disable maxcast
     */
    private To(Factory f, Collection<DID> dids)
    {
        _f = f;

        for (DID did : dids) add_(did);
    }

    /**
     * @return all DIDs passed to the object, including those already picked
     */
    public Set<DID> allDIDs()
    {
        return Sets.union(_dids, _avoid);
    }

    /**
     * add a device and remove it from the avoid list
     */
    public To add_(DID did)
    {
        assert !did.equals(_f._cfgLocalDID.get());

        _avoid.remove(did);
        _dids.add(did);
        return this;
    }

    public void avoid_(@Nonnull DID did)
    {
        assert did != null;
        _dids.remove(did);
        _avoid.add(did);
    }

    /**
     * pick the next preferred device
     */
    public @Nonnull DID pick_() throws ExNoAvailDevice
    {
        DevEntry min = null;
        for (DID did : _dids) {
            Device dev = _f.__devices.getOPMDevice_(did);
            DevEntry den;
            if (dev == null) {
                den = new DevEntry(did, Integer.MAX_VALUE);
            } else {
                den = new DevEntry(did, dev.getPreferenceUtility_());
            }
            if (min == null) min = den;
            else if (min.compareTo(den) > 0) min = den;
        }

        if (min == null) throw new ExNoAvailDevice();

        DID did = min._did;
        avoid_(did);
        return did;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (DID did : _dids) {
            if (first) first = false;
            else sb.append(' ');
            sb.append(did);
        }

        if (!_avoid.isEmpty()) {
            if (!first) sb.append(' ');
            sb.append("avoid");
            for (DID did : _avoid) {
                sb.append(' ');
                sb.append(did);
            }
        }

        sb.append(']');

        return sb.toString();
    }
}
