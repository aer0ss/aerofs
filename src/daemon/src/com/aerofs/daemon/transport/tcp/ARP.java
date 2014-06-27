package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.C;
import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.IMulticastListener;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.Map;

import static com.aerofs.daemon.transport.lib.TransportUtil.prettyPrint;
import static com.google.common.collect.Maps.newConcurrentMap;

// TODO: A new IP will override any previously-known IP, which sucks; bad data displaces good. Make this a multimap.
final class ARP
{
    interface IARPVisitor
    {
        void visit(DID did, ARPEntry arp);
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
    private final IMulticastListener presenceService;
    private final Map<DID, ARPEntry> arpEntries = newConcurrentMap();

    ARP(IMulticastListener multicast) {this.presenceService = multicast;}

    /**
     * @throws ExDeviceUnavailable if there is no routing information for this peer
     */
    ARPEntry getThrows(DID did) throws ExDeviceUnavailable
    {
        ARPEntry en = arpEntries.get(did);
        if (en == null) throw new ExDeviceUnavailable("no arp entry for " + did);
        return en;
    }

    /**
     * This overwrites old entries.
     */
    void put(DID did, InetSocketAddress remoteAddress)
    {
        ARPEntry oldEntry = arpEntries.put(did, new ARPEntry(remoteAddress, new ElapsedTimer()));
        if (oldEntry == null) l.info("{} arp add {}", did, prettyPrint(remoteAddress));
        presenceService.onDeviceReachable(did);
    }

    /**
     * Removes an {@link ARPEntry}
     *
     * @param did {@link com.aerofs.base.id.DID} of the peer whose ARPEntry should be removed
     */
    void remove(DID did)
    {
        ARPEntry oldEntry = arpEntries.remove(did);
        if (oldEntry != null) l.info("{} arp del {}", did, prettyPrint(oldEntry.remoteAddress));
        presenceService.onDeviceUnreachable(did);
    }

    boolean exists(DID did)
    {
        return arpEntries.containsKey(did);
    }

    void visitARPEntries(IARPVisitor v)
    {
        Map<DID, ARPEntry> snapshot;
        synchronized (arpEntries) { snapshot = ImmutableMap.copyOf(this.arpEntries); }

        for (Map.Entry<DID, ARPEntry> e : snapshot.entrySet()) {
            v.visit(e.getKey(), e.getValue());
        }
    }

    @Override
    public String toString()
    {
        Map<DID, ARPEntry> snapshot;
        StringBuilder sb = new StringBuilder();
        synchronized (arpEntries) { snapshot = ImmutableMap.copyOf(this.arpEntries); }

        for (Map.Entry<DID, ARPEntry> en : snapshot.entrySet()) {
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
