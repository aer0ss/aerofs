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
import com.aerofs.verkehr.client.wire.ConnectionListener;
import com.aerofs.verkehr.client.wire.UpdateListener;
import com.aerofs.verkehr.client.wire.VerkehrPubSubClient;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

public class ChangeNotificationSubscriber implements ConnectionListener, UpdateListener
{
    private final static Logger l = Loggers.getLogger(ChangeNotificationSubscriber.class);

    private final VerkehrPubSubClient _vk;
    private final IMapSIndex2SID _sidx2sid;
    private final CoreScheduler _sched;

    private final AtomicBoolean _connected = new AtomicBoolean();
    private final Map<SID, Store> _stores = new ConcurrentHashMap<>();

    @Inject
    public ChangeNotificationSubscriber(VerkehrPubSubClient vk, IMapSIndex2SID sidx2sid,
                                        CoreScheduler sched)
    {
        _vk = vk;
        _sched = sched;
        _sidx2sid = sidx2sid;
    }

    public void init_()
    {
        _vk.addConnectionListener(this, MoreExecutors.sameThreadExecutor());
    }

    public void subscribe_(Store s)
    {
        SID sid = _sidx2sid.get_(s.sidx());
        _stores.put(sid, s);
        if (_connected.get()) {
            subscribe(_vk, sid);
        }
    }

    public void unsubscribe_(Store s)
    {
        _stores.remove(_sidx2sid.get_(s.sidx()));
    }

    @Override
    public void onConnected(VerkehrPubSubClient client) {
        _connected.set(true);
        l.warn("connected to vk");
        for (SID sid : _stores.keySet()) {
            subscribe(client, sid);
        }
    }

    private void subscribe(VerkehrPubSubClient client, SID sid)
    {
        addCallback(client.subscribe("pol/" + sid.toStringFormal(), this, sameThreadExecutor()),
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void aVoid)
                    {
                        l.warn("subscribed to polaris notif for {}", sid);
                        scheduleFetch(sid);
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        _vk.disconnect();
                    }
                });
    }

    @Override
    public void onDisconnected(VerkehrPubSubClient client) {
        _connected.set(false);
        l.warn("disconnected from vk");
    }

    @Override
    public void onUpdate(String topic, byte[] payload) {
        try {
            scheduleFetch(new SID(topic.substring(4)));
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
