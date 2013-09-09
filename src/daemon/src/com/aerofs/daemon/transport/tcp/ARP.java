package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.lib.IPresenceManager;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.aerofs.daemon.transport.lib.TransportUtil.prettyPrint;
import static com.aerofs.daemon.transport.tcp.ARP.ARPChange.ADD;
import static com.aerofs.daemon.transport.tcp.ARP.ARPChange.REM;
import static com.aerofs.daemon.transport.tcp.ARP.ARPChange.UPD;

class ARP implements IPresenceManager
{
    void addARPChangeListener(IARPChangeListener listener)
    {
        _listeners.add(listener);
    }

    synchronized @Nullable ARPEntry get(DID did)
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
     */
    void put(DID did, InetSocketAddress isa)
    {
        boolean isNew;
        synchronized (this) {
            ElapsedTimer timer = new ElapsedTimer();
            ARPEntry oldEntry = _did2en.put(did, new ARPEntry(isa, timer));
            isNew = (oldEntry == null);
        }

        notifyListeners(did, isNew ? ADD : UPD);

        if (isNew) {
            l.info("arp: add: d{} rem:{}", did, prettyPrint(isa));
        }
    }

    /**
     * Removes an {@link ARPEntry}
     *
     * @param did {@link DID} of the peer whose <code>ARPEntry </code> should be removed
     * @return null if there was no entry
     */
    @Nullable ARPEntry remove(DID did)
    {
        ARPEntry oldEntry;
        synchronized (this) {
            oldEntry = _did2en.remove(did);
        }

        notifyListeners(did, REM);

        if (oldEntry != null) {
            l.info("arp: rem: d:{} rem:{}", did, prettyPrint(oldEntry._isa));
        }

        return oldEntry;
    }

    boolean exists(DID did)
    {
        return get(did) != null;
    }

    @Override
    public boolean isPresent(DID did)
    {
        return exists(did);
    }

    void visitARPEntries(IARPVisitor v)
    {
        Map<DID, ARPEntry> arpEntries;
        synchronized (this) {
            arpEntries = Maps.newHashMap(_did2en);
        }

        for (Map.Entry<DID, ARPEntry> e : arpEntries.entrySet()) {
            v.visit_(e.getKey(), e.getValue());
        }
    }

    private void notifyListeners(DID did, ARPChange chg)
    {
        for (IARPChangeListener w : _listeners) {
            w.onArpChange_(did, chg);
        }
    }

    @Override
    public synchronized String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<DID, ARPEntry> en : _did2en.entrySet()) {
            String a = en.getKey().toString();
            sb.append(a)
              .append(" -> ")
              .append(en.getValue()._isa)
              .append(", ")
              .append((en.getValue()._lastUpdatedTimer.elapsed()) / C.SEC)
              .append("s");
            sb.append('\n');
        }
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
        final ElapsedTimer _lastUpdatedTimer;

        ARPEntry(InetSocketAddress isa, ElapsedTimer timer)
        {
            _isa = isa;
            _lastUpdatedTimer = timer;
        }
    }

    //
    // members
    //

    private final Map<DID, ARPEntry> _did2en = Maps.newConcurrentMap();
    private final CopyOnWriteArraySet<IARPChangeListener> _listeners = Sets.newCopyOnWriteArraySet();

    private static final Logger l = Loggers.getLogger(ARP.class);
}
