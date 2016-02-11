/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.ids.SID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.ssmp.*;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPEvent.Type;
import com.aerofs.ssmp.SSMPRequest.SubscriptionFlag;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.util.concurrent.Futures.addCallback;

public class ChangeNotificationSubscriber implements ConnectionListener, EventHandler
{
    private final static Logger l = Loggers.getLogger(ChangeNotificationSubscriber.class);

    private final SSMPConnection _ssmp;
    private final IMapSIndex2SID _sidx2sid;
    private final CoreScheduler _sched;

    private final AtomicBoolean _connected = new AtomicBoolean();
    private final Map<SID, Store> _stores = new ConcurrentHashMap<>();

    @Inject
    public ChangeNotificationSubscriber(SSMPConnection ssmp, IMapSIndex2SID sidx2sid,
                                        CoreScheduler sched)
    {
        _ssmp = ssmp;
        _sched = sched;
        _sidx2sid = sidx2sid;
    }

    public void init_()
    {
        _ssmp.addConnectionListener(this);
        _ssmp.addEventHandler(this);
    }

    public void subscribe_(Store s)
    {
        SID sid = _sidx2sid.get_(s.sidx());
        _stores.put(sid, s);
        if (_connected.get()) {
            subscribe(sid);
        }
    }

    public void unsubscribe_(Store s)
    {
        _stores.remove(_sidx2sid.get_(s.sidx()));
    }

    @Override
    public void connected() {
        _connected.set(true);
        l.warn("connected to lipwig");
        _stores.keySet().forEach(this::subscribe);
    }

    @Override
    public void disconnected() {
        _connected.set(false);
        l.warn("disconnected from lipwig");
    }

    private void subscribe(SID sid)
    {
        SSMPIdentifier topic = SSMPIdentifier.fromInternal("pol/" + sid.toStringFormal());
        addCallback(_ssmp.request(SSMPRequest.subscribe(topic, SubscriptionFlag.NONE)),
                new FutureCallback<SSMPResponse>() {
                    @Override
                    public void onSuccess(SSMPResponse r) {
                        if (r.code == SSMPResponse.OK) {
                            l.warn("subscribed to polaris notif for {}", sid);
                            scheduleFetch(sid);
                        } else {
                            l.error("failed to polaris sub {} {}", sid, r.code);
                            // TODO: exp retry?
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        // TODO: force reconnect?
                    }
                });
    }

    @Override
    public void eventReceived(SSMPEvent ev) {
        if (ev.type != Type.MCAST || !ev.from.isAnonymous()
                || !ev.to.toString().startsWith("pol/")) {
            return;
        }
        try {
            l.debug("pol notif {} {}", ev.to.toString().substring(4), ev.payload);
            scheduleFetch(new SID(ev.to.toString().substring(4)));
        } catch (Exception e) {
            l.warn("invalid notification", e);
        }
    }

    private void scheduleFetch(SID sid)
    {
        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_() {
                Store s = _stores.get(sid);
                if (s != null) s.iface(ChangeFetchScheduler.class).schedule_();
            }
        }, 0);
    }
}
