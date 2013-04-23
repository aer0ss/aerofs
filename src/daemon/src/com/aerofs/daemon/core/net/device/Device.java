package com.aerofs.daemon.core.net.device;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * State transition:
 *
 *  when device is online:  start pulses:   goes to false online
 *                          stop pulses:    goes to true online
 *  when device is offline: start pulses:   nop
 *                          stop pulses:    nop
 *  when pulses is started: comes online:   nop
 *                          goes offline:   nop
 *  when pulses is stoped:  comes online:   goes to true online
 *                          goes offline:   goes to offline
 */

/**
 * FIXME (AG): a lot of the logic in here is simpler if we avoid filtering sidcs on state changes
 */
public class Device implements Comparable<Device>, IDumpStatMisc
{
    private final DID _did;

    private class TransportState
    {
        boolean _isBeingPulsed;
        final Set<SIndex> _sidcsAvailable = Sets.newHashSet();

        boolean isUnused_()
        {
            return _isBeingPulsed == false && _sidcsAvailable.isEmpty();
        }

        boolean isOnlineForStore_(SIndex sidx)
        {
            return _isBeingPulsed == false && _sidcsAvailable.contains(sidx);
        }
    }

    final private SortedMap<ITransport, TransportState> _tpsAvailable =
            new TreeMap<ITransport, TransportState>(Transports.PREFERENCE_COMPARATOR);

    public Device(DID did)
    {
        _did = did;
    }

    public DID did()
    {
        return _did;
    }

    @Override
    public int compareTo(Device o)
    {
        return _did.compareTo(o._did);
    }

    /**
     * @return a collection of stores that become online to the device
     */
    Collection<SIndex> online_(ITransport tp, Collection<SIndex> sidcs)
    {
        TransportState tpState = getOrCreate_(tp);

        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : sidcs) {
            boolean added = tpState._sidcsAvailable.add(sidx); // _always_ add
            if (added && !tpState._isBeingPulsed) {
                ret.add(sidx);
            }
        }
        return getSidcsWhoseStateChanged_(tpState, ret);
    }

    /**
     * @return a collection of stores that becomes offline to the device
     */
    Collection<SIndex> offline_(ITransport tp, Collection<SIndex> sidcs)
    {
        TransportState tpState = _tpsAvailable.get(tp);
        if (tpState == null) return Collections.emptyList();

        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : sidcs) {
            boolean removed = tpState._sidcsAvailable.remove(sidx); // _always_ remove

            //
            // FIXME (AG): Simplify interaction between Device, DevicePresence and users of both
            // the test for _isBeingPulsed is necessary because of a complicated (and implicit)
            // contract between Device and its callers.
            //
            // Basically, when a caller decides to pulse a device we return all the stores that
            // have gone into the offline state. Since that point, additional stores may be
            // added. When the caller calls this method, we only return those stores that _weren't_
            // returned previously
            //

            if (removed && !tpState._isBeingPulsed) {
                ret.add(sidx);
            }
        }

        if (tpState.isUnused_()) {
            _tpsAvailable.remove(tp);
        }

        return getSidcsWhoseStateChanged_(tpState, ret);
    }

    Collection<SIndex> offline_(ITransport tp)
    {
        TransportState en = _tpsAvailable.get(tp);
        if (en == null) {
            return Collections.emptyList();
        }

        // make a copy to avoid concurrent modification exception
        return offline_(tp, new ArrayList<SIndex>(en._sidcsAvailable));
    }

    /**
     * must call isPulseStarted before calling this method
     * @return a collection of sids that becomes offline to the device
     */
    Collection<SIndex> pulseStarted_(ITransport tp)
    {
        TransportState tpState = getOrCreate_(tp);

        assert !tpState._isBeingPulsed;

        tpState._isBeingPulsed = true;
        return getSidcsWhoseStateChanged_(tpState);
    }

    /**
     * @return a collection of sids that becomes online to the device
     */
    Collection<SIndex> pulseStopped_(ITransport tp)
    {
        TransportState tpState = _tpsAvailable.get(tp);

        assert tpState != null && tpState._isBeingPulsed;

        tpState._isBeingPulsed = false;
        if (tpState.isUnused_()) {
            _tpsAvailable.remove(tp);
        }

        return getSidcsWhoseStateChanged_(tpState);
    }

    private TransportState getOrCreate_(ITransport tp)
    {
        TransportState tpState = _tpsAvailable.get(tp);
        if (tpState == null) {
            tpState = new TransportState();
            _tpsAvailable.put(tp, tpState);
        }
        return tpState;
    }

    //
    // FIXME (AG): both versions of getSidcsWhoseStateChanged_ look similar and should be merged
    //

    /**
     * When an action (online, offline) occurs this may trigger a tpState change to an sidx:
     * an sidx that is previously available may no longer be available. This method returns
     * those sidcs that were affected as a result of an operation
     */
    private Collection<SIndex> getSidcsWhoseStateChanged_(TransportState comparedTpState,
            List<SIndex> sidcs)
    {
        Iterator<SIndex> iter = sidcs.iterator();
        while (iter.hasNext()) {
            SIndex sidx = iter.next();
            boolean isAvailable = false;
            for (TransportState tpState : _tpsAvailable.values()) {
                if (tpState != comparedTpState && tpState.isOnlineForStore_(sidx)) {
                    isAvailable = true;
                    break;
                }
            }
            if (isAvailable) iter.remove();
        }

        return sidcs;
    }

    /*
     * Returns a collection of sindexes that are currently unavailable on all transports
     */
    private Collection<SIndex> getSidcsWhoseStateChanged_(TransportState comparedTpState)
    {
        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : comparedTpState._sidcsAvailable) {
            boolean isAvailable = false;
            for (TransportState tpState : _tpsAvailable.values()) {
                if (tpState != comparedTpState && tpState.isOnlineForStore_(sidx)) {
                    isAvailable = true;
                    break;
                }
            }
            if (!isAvailable) ret.add(sidx);
        }

        return ret;
    }

    boolean isOnline_()
    {
        for (Entry<ITransport, TransportState> en : _tpsAvailable.entrySet()) {
            if (!en.getValue()._isBeingPulsed) {
                assert !en.getValue()._sidcsAvailable.isEmpty();
                return true;
            }
        }
        return false;
    }

    boolean isBeingPulsed_(ITransport tp)
    {
        TransportState en = _tpsAvailable.get(tp);
        return en != null && en._isBeingPulsed;
    }

    boolean isAvailable_()
    {
        return !_tpsAvailable.isEmpty();
    }

    /**
     * TODO use device-specific preferences instead of static preference
     */
    @Nonnull public ITransport getPreferedTransport_()
    {
        for (Entry<ITransport, TransportState> en : _tpsAvailable.entrySet()) {
            if (!en.getValue()._isBeingPulsed) {
                assert !en.getValue()._sidcsAvailable.isEmpty();
                return en.getKey();
            }
        }

        // this method shouldn't get called if the device is offline on all the
        // transport. this may not be a valid assumption because the daemon
        // might contact an offline peer, but so far so good
        throw SystemUtil.fatalWithReturn("gpt on offline device");
    }

    /**
     * TODO incorporate the following factors:
     *  o latency/throughput
     *  o failure history
     *  o workload
     *  o etc etc
     */
    public int getPreferenceUtility_()
    {
        return getPreferedTransport_().rank();
    }

    @Override
    public String toString()
    {
        return _did.toString();
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.print(indent + _did + ": ");
        for (Entry<ITransport, TransportState> en : _tpsAvailable.entrySet()) {
            TransportState ten = en.getValue();
            if (ten._isBeingPulsed) {
                ps.print("(" + en.getKey() + ten._sidcsAvailable.size() + ") ");
            } else {
                ps.print("" + en.getKey() + ten._sidcsAvailable.size() + " ");
            }
        }
        ps.println();
    }
}
