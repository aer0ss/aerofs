package com.aerofs.daemon.core.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.device.Device;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class To
{
    public static final int ANYCAST = 0x1;
    public static final int RANDCAST = 0x2;
    public static final int MAXCAST = 0x4;

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

    private int _cast;     // type == 0 if unicast only

    private final SIndex _sidx;

    private final Set<DID> _dids = new HashSet<DID>();

    private final Set<DID> _avoid = new HashSet<DID>();

    public static class Factory
    {
        private final Devices __devices;
        private final MapSIndex2Store _sidx2s;

        @Inject
        public Factory(Devices devices, MapSIndex2Store sidx2s)
        {
            __devices = devices;
            _sidx2s = sidx2s;
        }

        public To create_(To to)
        {
            return new To(to);
        }

        public To create_(SIndex sidx)
        {
            return new To(this, sidx);
        }

        public To create_(DID did)
        {
            return new To(this, did);
        }

        public To create_(Collection<DID> dids)
        {
            return new To(this, dids);
        }

        public To create_(SIndex sidx, int cast)
        {
            return new To(this, sidx, cast);
        }
    }

    private final Factory _f;

    /**
     * @param to the To object being cloned. its avoid list is ignored
     */
    private To(To to)
    {
        _f = to._f;
        _cast |= to._cast;
        _sidx = to._sidx;
        _dids.addAll(to._dids);
        _avoid.removeAll(to._dids);
    }

    /**
     * equivalent to To(sdm, s, ANYCAST)
     */
    private To(Factory f, SIndex sidx)
    {
        this(f, sidx, ANYCAST);
    }

    // set a store-based recipient. It will be picked only if no more preferred
    // devices is available
    private To(Factory f, SIndex sidx, int cast)
    {
        if (DaemonParam.MAXCAST_IF_NO_AVAILABLE_DEVICES) cast |= MAXCAST;

        _f = f;
        _sidx = sidx;
        _cast = cast;

        // these two are mutual exclusive
        assert !isSet(ANYCAST) || !isSet(RANDCAST);
    }

    /**
     * add a device and permanently disable maxcast
     */
    private To(Factory f, DID did)
    {
        _f = f;
        _sidx = null;
        _cast = 0;

        add_(did);
    }

    /**
     * add a list of devices and permanently disable maxcast
     */
    private To(Factory f, Collection<DID> dids)
    {
        _f = f;
        _sidx = null;
        _cast = 0;

        for (DID did : dids) add_(did);
    }

    private boolean isSet(int b)
    {
        return Util.test(_cast, b);
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
        assert !did.equals(Cfg.did());

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
     * @return null for maxcast
     */
    public @Nullable DID pick_() throws ExNoAvailDevice
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

        DID did;
        if (min == null) {
            did = pickStoreBased_();
        } else {
            did = min._did;
        }

        if (did != null) avoid_(did);
        return did;
    }

    /**
     * @return null for maxcast
     */
    private @Nullable DID pickStoreBased_() throws ExNoAvailDevice
    {
        if (_cast == 0) throw new ExNoAvailDevice();

        assert _sidx != null;
        Store s = _f._sidx2s.get_(_sidx);

        if (_cast == MAXCAST) {
            // maxcast can be used only once
            _cast = 0;
            return null;

        } else {
            // cannot set both
            assert !isSet(ANYCAST) || !isSet(RANDCAST);

            if (isSet(ANYCAST)) {
                DevEntry min = null;
                for (Entry<DID, Device> en : s.getOnlinePotentialMemberDevices_().entrySet()) {
                    DID did = en.getKey();
                    if (_avoid.contains(did)) continue;

                    DevEntry den = new DevEntry(did, en.getValue()
                            .getPreferenceUtility_());
                    if (min == null) min = den;
                    else if (min.compareTo(den) > 0) min = den;
                }

                if (min != null) return min._did;

            } else {
                assert isSet(RANDCAST);

                int size = s.getOnlinePotentialMemberDevices_().size();
                ArrayList<DID> dids = Lists.newArrayListWithExpectedSize(size);
                for (DID did : s.getOnlinePotentialMemberDevices_().keySet()) {
                    if (!_avoid.contains(did)) dids.add(did);
                }

                if (!dids.isEmpty()) {
                    return dids.get((int) (Math.random() * dids.size()));
                }
            }

            // fall back to maxcast
            if (isSet(MAXCAST)) {
                _cast &= ~MAXCAST;
                return null;
            } else {
                throw new ExNoAvailDevice();
            }
        }
    }

    public @Nonnull SIndex sidx()
    {
        assert _sidx != null;
        return _sidx;
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

        if (_sidx != null) {
            if (first) first = false;
            else sb.append(' ');
            sb.append(_sidx);
            sb.append(':');
            if (isSet(ANYCAST)) sb.append('A');
            if (isSet(RANDCAST)) sb.append('R');
            if (isSet(MAXCAST)) sb.append('M');
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
