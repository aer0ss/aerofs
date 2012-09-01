/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.core.net.link.ILinkStateService;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.base.pipeline.IPipelineFactory;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.log4j.Logger;

import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class UnicastService
        implements IUnicastService, IIncomingUnicastConnectionListener, IPeerPresenceListener
{
    private static final Logger l = Util.l(UnicastService.class);

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
        _unicastConnectionService.start_(); // FIXME: remove this - i.e. start via other means
    }

    @Override
    public void onLinkStateChanged_(ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed, ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> previous)
    {
        if (current.isEmpty()) {
            destroyAllPeers_(new ExTransport("network down"));
        }
    }

    @Override
    public void onPresenceServiceConnected_()
    {
        // this is a noop - we do nothing
    }

    @Override
    public void onPresenceServiceDisconnected_()
    {
        // if the presence service goes down we assume all peers are unreachable
        l.info("Presence service disconnected");
        destroyAllPeers_(new ExTransport("presence service unavailable"));
    }

    @Override
    public void onPeerOnline_(DID did)
    {
        // this is a noop again - we do nothing until asked to by someone else
        l.info("Peer " + did + " online");
    }

    @Override
    public void onPeerOffline_(DID did)
    {
        // presence service says they're offline, destroy their resources
        l.info("Peer " + did + " offline");
        destroyPeer_(did, new ExTransport("peer offline"));
    }

    @Override
    public ListenableFuture<Void> sendDatagram_(DID did, SID sid, byte[] payload, Prio pri)
    {
        Peer peer = makeAndGet_(did);
        return peer.sendDatagram_(sid, payload, pri);
    }

    @Override
    public ListenableFuture<IOutgoingStream> beginStream_(StreamID id, DID did, SID sid, Prio pri)
    {
        Peer peer = makeAndGet_(did);
        return peer.beginStream_(id, sid, pri);
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
