package com.aerofs.daemon.transport.ssmp;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ISignallingService;
import com.aerofs.daemon.transport.ISignallingServiceFactory;
import com.aerofs.daemon.transport.lib.*;
import com.aerofs.daemon.transport.presence.IStoreInterestListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.event.Prio;
import com.aerofs.ssmp.*;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPEvent.Type;
import com.aerofs.ssmp.SSMPRequest.SubscriptionFlag;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.net.NetworkInterface;
import java.nio.channels.ClosedChannelException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SSMPConnectionService implements ConnectionListener, EventHandler,
        IMaxcast, IPresenceSource, ISignallingServiceFactory, ILinkStateListener {
    private final static Logger l = Loggers.getLogger(SSMPConnectionService.class);

    private final CoreQueue _q;
    private final SSMPConnection _c;

    private boolean linkUp = false;

    private final Set<SID> _interest = Sets.newSetFromMap(new ConcurrentHashMap<>());
    public final SSMPPresenceProcessor presenceProcessor = new SSMPPresenceProcessor();

    @Inject
    public SSMPConnectionService(CoreQueue q, LinkStateService lss, SSMPConnection c) {
        _q = q;
        _c = c;

        // maxcast
        c.addConnectionListener(this);
        c.addEventHandler(this);

        // presence
        c.addConnectionListener(presenceProcessor);
        c.addEventHandler(presenceProcessor);

        lss.addListener(this, MoreExecutors.sameThreadExecutor());
    }

    // TODO: think about who should own SSMP start/stop when it eventually replaces vk...
    public void start() {
        _c.start();
    }

    public void stop() {
        _c.stop();
    }

    @Override
    public void eventReceived(SSMPEvent ev) {
        if (ev.type != Type.MCAST) {
            return;
        }
        try {
            DID did = new DID(ev.from.toString());
            SID sid = new SID(ev.to.toString());

            // unsubscribe/mcast race
            if (!_interest.contains(sid)) return;

            l.info("{} recv mc", did);

            byte[] bs = SSMPUtil.decodeMcastPayload(did, ev.payload);

            if (bs == null) return;

            // TODO: why is the SID not passed upwards?
            Endpoint ep = new Endpoint(null, did);
            ByteArrayInputStream is = new ByteArrayInputStream(bs);
            _q.enqueue(new EIMaxcastMessage(ep, is), Prio.LO);
        } catch (Exception e) {}
    }

    @Override
    public void connected() {
        _interest.forEach(SSMPConnectionService.this::subscribe);
    }

    @Override
    public void disconnected() {
    }

    @Override
    public void sendPayload(SID sid, int mcastid, byte[] bs) {
        try {
            _c.request(SSMPRequest.mcast(
                    SSMPIdentifier.fromInternal(sid.toStringFormal()),
                    encodeMaxcast(bs)));
        } catch (Exception e) {
            l.warn("mc failed", e);
        }
    }

    private String encodeMaxcast(byte[] bs) {
        return SSMPUtil.encodeMcastPayload(bs);
    }

    @Override
    public void updateInterest(SID[] sidsAdded, SID[] sidsRemoved) {
        for (SID sid : sidsAdded) {
            _interest.add(sid);
            if (_c.isLoggedIn()) subscribe(sid);
        }

        for (SID sid : sidsRemoved) {
            _interest.remove(sid);
            if (!_c.isLoggedIn()) continue;
            try {
                Futures.addCallback(_c.request(SSMPRequest.unsubscribe(
                        SSMPIdentifier.fromInternal(sid.toStringFormal()))), new FutureCallback<SSMPResponse>() {
                    @Override
                    public void onSuccess(SSMPResponse r) {
                        if (r.code != SSMPResponse.OK) {
                            l.warn("unsubscribe failed {} {} {}", sid, r.code, r.payload);
                        } else {
                            l.info("unsubscribed {}", sid);
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        l.warn("unsubscribe failed {}", sid, BaseLogUtil.suppress(t,
                                ClosedChannelException.class));
                    }
                }, MoreExecutors.sameThreadExecutor());
            } catch (Exception e) {
                l.warn("unsubscribe failed {}", sid, BaseLogUtil.suppress(e,
                        ClosedChannelException.class));
            }
        }
    }

    private void subscribe(SID sid) {
        try {
            Futures.addCallback(_c.request(SSMPRequest.subscribe(
                    SSMPIdentifier.fromInternal(sid.toStringFormal()),
                    SubscriptionFlag.PRESENCE)), new FutureCallback<SSMPResponse>() {
                @Override
                public void onSuccess(SSMPResponse r) {
                    if (r.code != SSMPResponse.OK) {
                        l.warn("subscribe failed {} {} {}", sid, r.code, r.payload);
                    } else {
                        l.info("subscribed {}", sid);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    l.warn("subscribe failed {}", sid, BaseLogUtil.suppress(t,
                            ClosedChannelException.class));
                }
            }, MoreExecutors.sameThreadExecutor());
        } catch (Exception e) {
            l.warn("subscribe failed {}", sid, BaseLogUtil.suppress(e,
                    ClosedChannelException.class));
        }
    }

    public ISignallingService newSignallingService(String transportId) {
        return new SignallingService(transportId, _c);
    }

    public void addMulticastListener(IMulticastListener l) {
        presenceProcessor.multicastListeners.add(l);
    }

    public void addStoreInterestListener(IStoreInterestListener l) {
        presenceProcessor.storeInterestListeners.add(l);
    }

    @Override
    public void onLinkStateChanged(ImmutableSet<NetworkInterface> previous,
                                   ImmutableSet<NetworkInterface> current,
                                   ImmutableSet<NetworkInterface> added,
                                   ImmutableSet<NetworkInterface> removed) {
        boolean wasUp = linkUp;
        linkUp = !current.isEmpty();

        if (!wasUp && linkUp) {
            start();
        } else if (wasUp && !linkUp) {
            stop();
        } else {
            // TODO: BCAST updated presence information
        }
    }
}
