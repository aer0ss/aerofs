/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.SSMPNotificationSubscriber;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.ids.SID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPEvent;
import com.aerofs.ssmp.SSMPEvent.Type;
import com.aerofs.ssmp.SSMPResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;

import org.slf4j.Logger;

public class ChangeNotificationSubscriber extends SSMPNotificationSubscriber
{
    private final static Logger l = Loggers.getLogger(ChangeNotificationSubscriber.class);

    private final CoreScheduler _sched;

    @Inject
    public ChangeNotificationSubscriber(SSMPConnection ssmp, IMapSIndex2SID sidx2sid,
                                        CoreScheduler sched)
    {
        super(ssmp, sidx2sid);
        _sched = sched;
    }

    @Override
    protected FutureCallback<SSMPResponse> subscribeCallback(SID sid) {
        return new FutureCallback<SSMPResponse>() {
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
        };
    }

    @Override
    protected String getStoreTopic(SID sid) {
        return "pol/" + sid.toStringFormal();
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
