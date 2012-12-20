package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.daemon.transport.tcpmt.Stores.Prefix;
import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFSID;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.google.common.collect.ImmutableSet;

import org.apache.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.*;

import javax.annotation.Nullable;

import static com.aerofs.daemon.transport.lib.AddressUtils.printaddr;
import static com.aerofs.daemon.transport.tcpmt.ARP.ARPChange.*;

public class ARP
{
    boolean isMulticastUnreachableOnlineDevice(DID did)
    {
        return _muod.contains(did);
    }

    Set<DID> getMulticastUnreachableOnlineDevices()
    {
        return _muod;
    }

    synchronized void addARPWatcher(IARPWatcher w)
    {
        _arpwatchers.add(w);
    }

    // may return null
    synchronized ARPEntry get(DID did)
    {
        ARPEntry en = _did2en.get(did);
        if (en != null) assert !en._isa.isUnresolved();
        return en;
    }

    ARPEntry getThrows(DID did)
        throws ExDeviceOffline
    {
        ARPEntry en = get(did);
        if (en == null) throw new ExDeviceOffline();
        return en;
    }

    /**
     * this overwrites old entries.
     *
     * @param multicast whether the message is received from IP multicast
     * @return true if there was no old entry
     */
    synchronized boolean put(DID did, InetSocketAddress isa, @Nullable BFSID filter, int filterSeq,
            @Nullable ImmutableSet<Prefix> prefixes, @Nullable ImmutableSet<SID> sidsOnline,
            boolean multicast, long now)
    {
        boolean isNew = _did2en.put(did, new ARPEntry(isa, filter, filterSeq, prefixes, sidsOnline,
                now)) == null;

        if (isNew) {
            assert !_muod.contains(did);
            if (!multicast) {
                Set<DID> muod = copyMUOD();
                muod.add(did);
                _muod = muod;
            }
        } else {
            if (multicast && _muod.contains(did)) {
                Set<DID> muod = copyMUOD();
                muod.remove(did);
                _muod = muod;
            }
        }

        notifyWatchers_(did, isNew ? ADD : UPD);

        if (l.isDebugEnabled()) {
            l.debug("arp: add: d:" + did + " rem:" + printaddr(isa) + " m:" + multicast + " n:" + isNew);
        }

        return isNew;
    }

    /**
     * Removes an {@link ARPEntry}
     *
     * @param did {@link DID} of the peer whose <code>ARPEntry </code> should be removed
     * @return null if there was no entry
     */
    synchronized @Nullable ARPEntry remove(DID did)
    {
        ARPEntry ret = _did2en.remove(did);

        boolean onmulticast = true;
        if (ret != null) {
            if (_muod.contains(did)) {
                onmulticast = false;
                Set<DID> muod = copyMUOD();
                muod.remove(did);
                _muod = muod;
            }
        } else {
            assert !_muod.contains(did);
        }


        notifyWatchers_(did, REM);

        if (l.isDebugEnabled()) {
            l.debug("arp: rem: d:" + (ret == null ? "null" : did + "rem:" + printaddr(ret._isa)) + " m:" + onmulticast);
        }

        return ret;
    }

    synchronized boolean exists(DID did)
    {
        return get(did) != null;
    }

    synchronized void visitARPEntries(IARPVisitor v)
    {
        for (Map.Entry<DID, ARPEntry> e : _did2en.entrySet()) {
            v.visit_(e.getKey(), e.getValue());
        }
    }

    private void notifyWatchers_(DID did, ARPChange chg)
    {
        // I could really make a copy of _arpwatchers and use that so that
        // this doesn't have to be called in a synchronized block
        for (IARPWatcher w : _arpwatchers) {
            w.arpChange_(did, chg);
        }
    }

    private Set<DID> copyMUOD()
    {
        return new TreeSet<DID>(_muod);
    }

    @Override
    public synchronized String toString()
    {
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();
        for (Map.Entry<DID, ARPEntry> en : _did2en.entrySet()) {
            String a = en.getKey().toString();
            sb.append(a + " -> " + en.getValue()._isa +
                ", " + ((now - en.getValue()._lastUpdated) / C.SEC) + "s");
            sb.append('\n');
        }
        sb.append("muod: ");
        sb.append(_muod);
        return sb.toString();
    }

    //
    // types
    //

    interface IARPVisitor
    {
        void visit_(DID did, ARPEntry arp);
    }

    /**
     * Type of {@link ARPEntry} change
     */
    enum ARPChange
    {
        /** Add a <strong>new</strong> ARPEntry */
        ADD,
        /** Update an <strong>existing</strong> ARPEntry */
        UPD,
        /** Remove an <strong>existing</strong> ARPEntry */
        REM,
    }

    /**
     * To be implemented by classes that want to know about ARP changes
     */
    interface IARPWatcher
    {
        /**
         * Called whenever an ARP entry changes. Changes can take 3 forms:
         * <ul>
         *     <li>Add</li>
         *     <li>Update</li>
         *     <li>Remove</li>
         * </ul>
         *
         * @param did {@link DID} for which the change occurs
         * @param chg {@link ARPChange} indicating what type the change is
         */
        void arpChange_(DID did, ARPChange chg);
    }

    static class ARPEntry
    {
        final InetSocketAddress _isa;
        long _lastUpdated;
        final int _filterSeq;           // can be Stores.FILTER_SEQ_INVALID
        final @Nullable BFSID _filter;
        final @Nullable ImmutableSet<Prefix> _prefixes;
        final @Nullable ImmutableSet<SID> _sidsOnline;

        ARPEntry(InetSocketAddress isa, @Nullable BFSID filter, int filterSeq,
            @Nullable ImmutableSet<Prefix> prefixes, @Nullable ImmutableSet<SID> sidsOnline,
            long lastUpdate)
        {
            _isa = isa;
            _lastUpdated = lastUpdate;
            _filter = filter;
            _filterSeq = filterSeq;
            _prefixes = prefixes;
            _sidsOnline = sidsOnline;
        }
    }

    //
    // members
    //

    // MUOD = Multicast Unreachable Online Devices
    //
    // according to the requirement from ITransport, the content of this set
    // must be immutable
    private Set<DID> _muod = Collections.emptySet();

    private final Map<DID, ARPEntry> _did2en = new TreeMap<DID, ARPEntry>();
    private final Set<IARPWatcher> _arpwatchers = new HashSet<IARPWatcher>();

    private static final Logger l = Util.l(ARP.class);
}
