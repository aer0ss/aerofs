package com.aerofs.daemon.transport.ssmp;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIMaxcastMessage;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ISignallingService;
import com.aerofs.daemon.transport.ISignallingServiceFactory;
import com.aerofs.daemon.transport.lib.*;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocationReceiver;
import com.aerofs.daemon.transport.presence.IStoreInterestListener;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
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
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.net.NetworkInterface;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SSMPConnectionService implements
        SSMPClient.ConnectionListener, SSMPEventHandler,
        IMaxcast, IPresenceSource, ISignallingServiceFactory, ILinkStateListener {
    private final static Logger l = Loggers.getLogger(SSMPConnectionService.class);

    private final DID _did;
    private final SSMPIdentifier _login;

    private final Timer _timer;
    private final SSMPClient _client;
    private final CoreQueue _q;

    private long _delay = 0;
    private boolean linkUp = false;
    private final AtomicBoolean _stopped = new AtomicBoolean(true);
    private final AtomicBoolean _loggedIn = new AtomicBoolean();

    private final Set<SID> _interest = Sets.newSetFromMap(new ConcurrentHashMap<>());

    public final SSMPPresenceProcessor presenceProcessor = new SSMPPresenceProcessor();
    
    private final List<SSMPClient.ConnectionListener> _connectionListeners = new ArrayList<>();
    private final List<SSMPEventHandler> _eventHandlers = new ArrayList<>();

    public final List<IPresenceLocationReceiver> presenceLocationListeners = new ArrayList<>();

    @Inject
    public SSMPConnectionService(CfgLocalDID localdid, CfgLocalUser localuser, Timer timer,
                                 ClientSocketChannelFactory channelFactory,
                                 ClientSSLEngineFactory sslHandlerFactory,
                                 CoreQueue q, LinkStateService lss, SSMPParams params) {
        _did = localdid.get();
        _timer = timer;
        _q = q;
        _client = new SSMPClient(params.serverAddress, timer, channelFactory,
                sslHandlerFactory::newSslHandler, this);

        _connectionListeners.add(presenceProcessor);
        _eventHandlers.add(presenceProcessor);

        _login = SSMPIdentifier.fromInternal(_did.toStringFormal());

        lss.addListener(this, MoreExecutors.sameThreadExecutor());
    }

    public void start() {
        if (_stopped.compareAndSet(true, false)) {
            connect();
        }
    }

    private void connect() {
        _client.connect(_login, SSMPIdentifier.fromInternal("cert"), "", this);
    }

    public void stop() {
        if (_stopped.compareAndSet(false, true)) {
            _client.disconnect();
        }
    }

    @Override
    public void eventReceived(SSMPEvent ev) {
        l.info("recv event {} {} {} {}", ev.from, ev.type, ev.to, ev.payload);
        if (ev.type == Type.MCAST) {
            try {
                DID did = new DID(ev.from.toString());
                SID sid = new SID(ev.to.toString());

                // NB: protocol *should* ensure that this doesn't happen
                if (did.equals(_did)) return;
                // unsubscribe/mcast race
                if (!_interest.contains(sid)) return;

                l.info("{} recv mc", did);

                byte[] bs = SSMPUtil.decodeMcastPayload(did, ev.payload);

                if (bs == null) return;

                // TODO: why is the SID not passed upwards?
                Endpoint ep = new Endpoint(null, did);
                ByteArrayInputStream is = new ByteArrayInputStream(bs);
                _q.enqueue(new EIMaxcastMessage(ep, is), Prio.LO);

                return;
            } catch (Exception e) {}
        }
        _eventHandlers.forEach(h -> h.eventReceived(ev));
    }

    @Override
    public void connected() {
        try {
            l.info("logged in");
            _loggedIn.set(true);
            _delay = 0;
            _connectionListeners.forEach(ConnectionListener::connected);
            _interest.forEach(SSMPConnectionService.this::subscribe);
        } catch (Exception e) {
            l.warn("login failed", e);
        }
    }

    @Override
    public void disconnected() {
        _connectionListeners.forEach(ConnectionListener::disconnected);
        reconnect();
    }

    private void reconnect() {
        _loggedIn.set(false);
        if (_stopped.get()) {
            l.info("stopped");
            return;
        }
        l.info("reconnect in {}", _delay);
        _timer.newTimeout(timeout -> connect(),_delay, TimeUnit.MILLISECONDS);
        _delay = Math.min(Math.max(_delay * 2, 100), 60000);
    }

    @Override
    public void sendPayload(SID sid, int mcastid, byte[] bs) {
        try {
            _client.request(SSMPRequest.mcast(
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
            if (_loggedIn.get()) subscribe(sid);
        }

        for (SID sid : sidsRemoved) {
            _interest.remove(sid);
            if (!_loggedIn.get()) continue;
            try {
                Futures.addCallback(_client.request(SSMPRequest.unsubscribe(
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
            Futures.addCallback(_client.request(SSMPRequest.subscribe(
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
        SignallingService s = new SignallingService(transportId, _client);
        _eventHandlers.add(s);
        return s;
    }

    public void addMulticastListener(IMulticastListener l) {
        presenceProcessor.multicastListeners.add(l);
    }

    public void addStoreInterestListener(IStoreInterestListener l) {
        presenceProcessor.storeInterestListeners.add(l);
    }

    public void addPresenceLocationListener(IPresenceLocationReceiver l) {
        presenceLocationListeners.add(l);
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
