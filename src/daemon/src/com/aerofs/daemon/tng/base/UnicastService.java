/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class UnicastService
        implements IUnicastService, IIncomingUnicastConnectionListener, IPeerPresenceListener
{
    private static final Logger l = Loggers.getLogger(UnicastService.class);

    private final IUnicastConnectionService _unicastConnectionService;
    private final PeerFactory _peerFactory;
    private final Map<DID, Peer> _peers = new HashMap<DID, Peer>();

    public static UnicastService getInstance_(ISingleThreadedPrioritizedExecutor executor,
            ILinkStateService networkLinkStateService, IPresenceService presenceService,
            IUnicastConnectionService unicastConnectionService, IPipelineFactory pipelineFactory)
    {
        // FIXME: don't create PeerFactory inline

        PeerFactory peerFactory = new PeerFactory(executor, unicastConnectionService,
                pipelineFactory);
        UnicastService unicastService = new UnicastService(unicastConnectionService, peerFactory);

        networkLinkStateService.addListener_(unicastService, executor);
        presenceService.addListener_(unicastService, executor);
        unicastConnectionService.setListener_(unicastService, executor);

        return unicastService;
    }

    UnicastService(IUnicastConnectionService unicastConnectionService, PeerFactory peerFactory)
    {
        this._unicastConnectionService = unicastConnectionService;
        this._peerFactory = peerFactory;
    }

    @Override
    public void start_()
    {
        l.info("starting service");

        _unicastConnectionService.start_(); // FIXME: remove this - i.e. start via other means
    }

    @Override
    public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> previous)
    {
        if (current.isEmpty()) {
            l.info("network down: all links down");

            destroyAllPeers_(new ExTransport("network down"));
        }
    }

    @Override
    public void onPresenceServiceConnected_()
    {
        l.info("presence service connected"); // noop; do nothing
    }

    @Override
    public void onPresenceServiceDisconnected_()
    {
        l.info("presence service disconnected");

        destroyAllPeers_(new ExTransport("presence service unavailable")); // if presence service goes down we assume everyone's unreachable
    }

    @Override
    public void onPeerOnline_(DID did)
    {
        l.info(did + ": online"); // noop; we don't create a peer unless a packet needs to be sent
    }

    @Override
    public void onPeerOffline_(DID did)
    {
        l.info(did + ": offline");

        destroyPeer_(did, new ExTransport("peer offline")); // presence service says they're offline, destroy their resources
    }

    @Override
    public ListenableFuture<Void> sendDatagram_(DID did, SID sid, byte[] payload, Prio pri)
    {
        Peer peer = makeAndGet_(did);
        return peer.sendDatagram_(payload, pri);
    }

    @Override
    public ListenableFuture<IOutgoingStream> beginStream_(StreamID id, DID did, SID sid, Prio pri)
    {
        Peer peer = makeAndGet_(did);
        return peer.beginStream_(id, pri);
    }

    @Override
    public ListenableFuture<Void> pulse_(DID did, Prio pri)
    {
        Peer peer = makeAndGet_(did);
        return peer.pulse_(pri);
    }

    @Override
    public void onNewIncomingConnection_(DID did, IUnicastConnection unicast)
    {
        Peer peer = makeAndGet_(did);
        peer.onIncomingConnection_(unicast);
    }

    @Override
    public void dumpStat(PBDumpStat template, Builder bd)
            throws Exception
    {
        // AAG FIXME: implement!
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        // AAG FIXME: implement!
    }

    private Peer makeAndGet_(DID did)
    {
        if (!_peers.containsKey(did)) {
            _peers.put(did, _peerFactory.getInstance_(did));
        }

        return _peers.get(did);
    }

    private void destroyPeer_(DID did, Exception ex)
    {
        Peer peer = _peers.get(did);
        if (peer != null) peer.destroy_(ex);
        _peers.remove(did);
    }

    private void destroyAllPeers_(Exception ex)
    {
        Iterator<Map.Entry<DID, Peer>> it = _peers.entrySet().iterator();
        while (it.hasNext()) {
            Peer peer = it.next().getValue();
            peer.destroy_(ex);
            it.remove();
        }
    }
}
