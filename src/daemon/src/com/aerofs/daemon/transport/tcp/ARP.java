package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.aerofs.daemon.transport.lib.TPUtil.prettyPrint;
import static com.google.common.collect.Maps.newConcurrentMap;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newCopyOnWriteArraySet;

final class ARP
{
    interface IARPVisitor
    {
        void visit(DID did, ARPEntry arp);
    }

    interface IARPListener
    {
        void onARPEntryChange(DID did, boolean added);
    }

    static class ARPEntry
    {
        final InetSocketAddress remoteAddress;
        final ElapsedTimer lastUpdatedTimer;

        ARPEntry(InetSocketAddress remoteAddress, ElapsedTimer timer)
        {
            this.remoteAddress = remoteAddress;
            this.lastUpdatedTimer = timer;
        }
    }

    private static final Logger l = Loggers.getLogger(ARP.class);

    private final Map<DID, ARPEntry> arpEntries = newConcurrentMap();
    private final CopyOnWriteArraySet<IARPListener> listeners = newCopyOnWriteArraySet();

    void addListener(IARPListener listener)
    {
        listeners.add(listener);
    }

    synchronized @Nullable ARPEntry get(DID did)
    {
        ARPEntry en = arpEntries.get(did);
        if (en != null && en.remoteAddress.isUnresolved()) {
            throw new IllegalStateException("unresolved addr:" + en.remoteAddress);
        }
        return en;
    }

    /**
     * @throws ExDeviceUnavailable if there is no routing information for this peer
     */
    ARPEntry getThrows(DID did)
        throws ExDeviceUnavailable
    {
        ARPEntry en = get(did);
        if (en == null) throw new ExDeviceUnavailable("no arp entry for " + did);
        return en;
    }

    /**
     * this overwrites old entries.
     */
    void put(DID did, InetSocketAddress remoteAddress)
    {
        boolean isNew;
        synchronized (this) {
            ElapsedTimer timer = new ElapsedTimer();
            ARPEntry oldEntry = arpEntries.put(did, new ARPEntry(remoteAddress, timer));
            isNew = (oldEntry == null);
        }

        if (isNew) {
            l.info("arp: add: d{} rem:{}", did, prettyPrint(remoteAddress));
            notifyListeners(did, true);
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
            oldEntry = arpEntries.remove(did);
        }

        notifyListeners(did, false);

        if (oldEntry != null) {
            l.info("arp: rem: d:{} rem:{}", did, prettyPrint(oldEntry.remoteAddress));
        }

        return oldEntry;
    }

    boolean exists(DID did)
    {
        return get(did) != null;
    }

    void visitARPEntries(IARPVisitor v)
    {
        Map<DID, ARPEntry> arpEntries;
        synchronized (this) {
            arpEntries = newHashMap(this.arpEntries);
        }

        for (Map.Entry<DID, ARPEntry> e : arpEntries.entrySet()) {
            v.visit(e.getKey(), e.getValue());
        }
    }

    private void notifyListeners(DID did, boolean isOnline)
    {
        for (IARPListener listener : listeners) {
            listener.onARPEntryChange(did, isOnline);
        }
    }

    @Override
    public synchronized String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<DID, ARPEntry> en : arpEntries.entrySet()) {
            String a = en.getKey().toString();
            sb.append(a)
              .append(" -> ")
              .append(en.getValue().remoteAddress)
              .append(", ")
              .append((en.getValue().lastUpdatedTimer.elapsed()) / C.SEC)
              .append("s");
            sb.append('\n');
        }
        return sb.toString();
    }
}
