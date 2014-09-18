/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.base.TimerUtil;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.jboss.netty.util.TimerTask;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ChangeNotificationSubscriber
{
    private final static Logger l = Loggers.getLogger(ChangeNotificationSubscriber.class);

    private final IMapSIndex2SID _sidx2sid;
    private final CoreScheduler _sched;

    // temporary
    private final TimerTask _poll;
    private final Set<Store> _stores = Sets.newHashSet();

    @Inject
    public ChangeNotificationSubscriber(IMapSIndex2SID sidx2sid, CoreScheduler sched)
    {
        _sched = sched;
        _sidx2sid = sidx2sid;
        _poll = timeout -> _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                l.info("polling {} stores", _stores.size());
                _stores.stream().forEach(Store::fetchChanges_);
                TimerUtil.getGlobalTimer().newTimeout(_poll, 10, TimeUnit.SECONDS);
            }
        }, 0);
    }

    public void init_()
    {
        // TODO: vk sub
        // for now, pretend we get a notif for every store every 10s
        TimerUtil.getGlobalTimer().newTimeout(_poll, 10, TimeUnit.SECONDS);
    }

    public void subscribe_(Store s)
    {
        _stores.add(s);
    }

    public void unsubscribe_(Store s)
    {
        _stores.remove(s);
    }
}
