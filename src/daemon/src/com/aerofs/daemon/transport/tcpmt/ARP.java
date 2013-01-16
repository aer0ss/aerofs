package com.aerofs.daemon.transport.tcpmt;

import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.aerofs.daemon.transport.lib.AddressUtils.printaddr;
import static com.aerofs.daemon.transport.tcpmt.ARP.ARPChange.ADD;
import static com.aerofs.daemon.transport.tcpmt.ARP.ARPChange.REM;
import static com.aerofs.daemon.transport.tcpmt.ARP.ARPChange.UPD;

class ARP
{
    Set<DID> getMulticastUnreachableOnlineDevices()
    {
        return _muod;
    }

    synchronized void addARPChangeListener(IARPChangeListener listener)
    {
        _listeners.add(listener);
    }

    // may return null
    synchronized ARPEntry get(DID did)
    {
        ARPEntry en = _did2en.get(did);
        if (en != null && en._isa.isUnresolved()) {
            throw new IllegalStateException("unresolved addr:" + en._isa);
        }
        return en;
    }

    /**
     * @throws ExDeviceOffline if there is no routing information for this peer
     */
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
    synchronized boolean put(DID did, InetSocketAddress isa, boolean multicast)
    {
        ARPEntry oldEntry = _did2en.put(did, new ARPEntry(isa, System.currentTimeMillis()));
        boolean isNew = (oldEntry == null);

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

        notifyListeners_(did, isNew ? ADD : UPD);

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


        notifyListeners_(did, REM);

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

    private void notifyListeners_(DID did, ARPChange chg)
    {
        // I could really make a copy of _listeners and use that so that
        // this doesn't have to be called in a synchronized block
        for (IARPChangeListener w : _listeners) {
            w.onArpChange_(did, chg);
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
            sb.append(a)
              .append(" -> ")
              .append(en.getValue()._isa)
              .append(", ")
              .append((now - en.getValue()._lastUpdated) / C.SEC)
              .append("s");
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
    interface IARPChangeListener
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
        void onArpChange_(DID did, ARPChange chg);
    }

    static class ARPEntry
    {
        final InetSocketAddress _isa;
        long _lastUpdated;

        ARPEntry(InetSocketAddress isa, long lastUpdate)
        {
            _isa = isa;
            _lastUpdated = lastUpdate;
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

    private final Map<DID, ARPEntry> _did2en = Maps.newTreeMap();
    private final Set<IARPChangeListener> _listeners = Sets.newHashSet();

    private static final Logger l = Util.l(ARP.class);
}
