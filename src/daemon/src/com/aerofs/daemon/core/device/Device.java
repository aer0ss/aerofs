package com.aerofs.daemon.core.device;

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
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javax.annotation.Nullable;

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

public class Device implements Comparable<Device>, IDumpStatMisc
{
    private final DID _did;

    private static class TPEntry
    {
        boolean _pulsing;
        final Set<SIndex> _sidcs = Sets.newHashSet();

        boolean isClean_()
        {
            return _pulsing == false && _sidcs.isEmpty();
        }
    }

    final private SortedMap<ITransport, TPEntry> _map =
        new TreeMap<ITransport, TPEntry>(Transports.PREFERENCE_COMPARATOR);

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
        TPEntry en = _map.get(tp);
        if (en == null) {
            en = new TPEntry();
            _map.put(tp, en);
        }

        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : sidcs) {
            // the test must be in this order so the addr is always added
            if (en._sidcs.add(sidx) && !en._pulsing) ret.add(sidx);
        }

        Iterator<SIndex> iter = ret.iterator();
        while (iter.hasNext()) {
            SIndex sidx = iter.next();
            boolean old = false;
            for (TPEntry en2 : _map.values()) {
                if (en2 != en && !en2._pulsing && en2._sidcs.contains(sidx)) {
                    old = true;
                    break;
                }
            }
            if (old) iter.remove();
        }

        return ret;
    }

    /**
     * @return a collection of stores that becomes offline to the device
     */
    Collection<SIndex> offline_(ITransport tp, Collection<SIndex> sidcs)
    {
        TPEntry en = _map.get(tp);
        if (en == null) return Collections.emptyList();

        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : sidcs) {
            // the test must be in this order so the sid is always removed
            if (en._sidcs.remove(sidx) && !en._pulsing) ret.add(sidx);
        }
        if (en.isClean_()) _map.remove(tp);

        Iterator<SIndex> iter = ret.iterator();
        while (iter.hasNext()) {
            SIndex sidx = iter.next();
            boolean old = false;
            for (TPEntry en2 : _map.values()) {
                if (en2 != en && !en2._pulsing && en2._sidcs.contains(sidx)) {
                    old = true;
                    break;
                }
            }
            if (old) iter.remove();
        }

        return ret;
    }

    boolean isPulseStarted_(ITransport tp)
    {
        TPEntry en = _map.get(tp);
        return en != null && en._pulsing;
    }

    /**
     * must call isPulseStarted before calling this method
     * @return a collection of sids that becomes offline to the device
     */
    Collection<SIndex> pulseStarted_(ITransport tp)
    {
        TPEntry en = _map.get(tp);
        if (en == null) {
            en = new TPEntry();
            _map.put(tp, en);
        }
        assert !en._pulsing;

        en._pulsing = true;

        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : en._sidcs) {
            boolean old = false;
            for (TPEntry en2 : _map.values()) {
                if (en2 != en && !en2._pulsing && en2._sidcs.contains(sidx)) {
                    old = true;
                    break;
                }
            }
            if (!old) ret.add(sidx);
        }

        return ret;
    }

    /**
     * @return a collection of sids that becomes online to the device
     */
    Collection<SIndex> pulseStopped_(ITransport tp)
    {
        TPEntry en = _map.get(tp);
        assert en != null && en._pulsing;

        en._pulsing = false;
        if (en.isClean_()) _map.remove(tp);

        List<SIndex> ret = Lists.newLinkedList();
        for (SIndex sidx : en._sidcs) {
            boolean old = false;
            for (TPEntry en2 : _map.values()) {
                if (en2 != en && !en2._pulsing && en2._sidcs.contains(sidx)) {
                    old = true;
                    break;
                }
            }
            if (!old) ret.add(sidx);
        }

        return ret;
    }

    Collection<SIndex> offline_(ITransport tp)
    {
        TPEntry en = _map.get(tp);
        if (en == null) {
            return Collections.emptyList();
        } else {
            // make a copy to avoid concurrent modification exception
            return offline_(tp, new ArrayList<SIndex>(en._sidcs));
        }
    }

    boolean isClean_()
    {
        return _map.isEmpty();
    }

    boolean isOnline_()
    {
        for (Entry<ITransport, TPEntry> en : _map.entrySet()) {
            if (!en.getValue()._pulsing) {
                assert !en.getValue()._sidcs.isEmpty();
                return true;
            }
        }
        return false;
    }

    /**
     * TODO use device-specific preferences instead of static preference
     *
     * @return null if the device is offline
     */
    public @Nullable ITransport getPreferedTransport_()
    {
        for (Entry<ITransport, TPEntry> en : _map.entrySet()) {
            if (!en.getValue()._pulsing) {
                assert !en.getValue()._sidcs.isEmpty();
                return en.getKey();
            }
        }

        // this method shouldn't get called if the device is offline on all the
        // transport. this may not be a valid assumption because the daemon
        // might contact an offline peer, but so far so good
        Util.fatal("see comment");
        return null;
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
        return getPreferedTransport_().pref();
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
        for (Entry<ITransport, TPEntry> en : _map.entrySet()) {
            TPEntry ten = en.getValue();
            if (ten._pulsing) {
                ps.print("(" + en.getKey() + ten._sidcs.size() + ") ");
            } else {
                ps.print("" + en.getKey() + ten._sidcs.size() + " ");
            }
        }
        ps.println();
    }
}
