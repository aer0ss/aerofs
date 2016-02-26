/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.ids.SID;
import com.aerofs.ssmp.*;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPRequest.SubscriptionFlag;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;

import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.util.concurrent.Futures.addCallback;

/**
 * Subscribes to stores of interest.  Child classes must implement the what needs to be done on each
 * successful subscription and on each received event.
 */
public abstract class SSMPNotificationSubscriber implements ConnectionListener, EventHandler
{
    private final static Logger l = Loggers.getLogger(SSMPNotificationSubscriber.class);

    private final SSMPConnection _ssmp;
    private final IMapSIndex2SID _sidx2sid;
    private final AtomicBoolean _connected = new AtomicBoolean();

    protected final Map<SID, Store> _stores = new ConcurrentHashMap<>();

    @Inject
    public SSMPNotificationSubscriber(SSMPConnection ssmp, IMapSIndex2SID sidx2sid)
    {
        _ssmp = ssmp;
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

    private void subscribe(SID sid)
    {
        SSMPIdentifier topic = SSMPIdentifier.fromInternal(getStoreTopic(sid));
        addCallback(_ssmp.request(SSMPRequest.subscribe(topic, SubscriptionFlag.NONE)),
                subscribeCallback(sid));
    }

    public void unsubscribe_(Store s)
    {
        SID sid = _sidx2sid.getNullable_(s.sidx());
        if (sid == null) return;

        _stores.remove(sid);

        SSMPIdentifier topic = SSMPIdentifier.fromInternal(getStoreTopic(sid));

        addCallback(_ssmp.request(SSMPRequest.unsubscribe(topic)), new FutureCallback<SSMPResponse>() {
            @Override
            public void onSuccess(SSMPResponse r) {
                if (r.code == SSMPResponse.OK) {
                    l.warn("unsubscribed to polaris notif for {}", sid);
                } else {
                    l.error("failed to polaris unsub {} {}", sid, r.code);
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

    protected abstract FutureCallback<SSMPResponse> subscribeCallback(SID sid);

    protected abstract String getStoreTopic(SID sid);
}
