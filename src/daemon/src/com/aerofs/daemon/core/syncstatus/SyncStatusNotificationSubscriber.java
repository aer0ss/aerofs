package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.verkehr.AbstractVerkehrListener;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.ExponentialRetry;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Sp.PBSyncStatNotification;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

/**
 * This class connects to verkher and subscribe to a specific channel through which the sync stat
 * server will push notifications whenever the sync status of an object we share is modified.
 */
public class SyncStatusNotificationSubscriber
{
    private static final Logger l = Util.l(SyncStatusNotificationSubscriber.class);

    private final String _topic;
    private final VerkehrListener _listener;
    private final VerkehrNotificationSubscriber _subscriber;

    @Inject
    public SyncStatusNotificationSubscriber(VerkehrNotificationSubscriber subscriber,
            CfgLocalUser localUser, CfgLocalDID localDID, CoreQueue q, CoreScheduler sched,
            SyncStatusSynchronizer sync)
    {
        _subscriber = subscriber;
        _topic = localDID.get().toStringFormal() + localUser.get();
        _listener = new VerkehrListener(q, new ExponentialRetry(sched), sync);
    }

    public void init_()
    {
        _subscriber.subscribe_(_topic, _listener);
    }

    private final class VerkehrListener extends AbstractVerkehrListener
    {
        private final ExponentialRetry _er;
        private final SyncStatusSynchronizer _sync;

        VerkehrListener(CoreQueue q, ExponentialRetry er, SyncStatusSynchronizer sync)
        {
            super(q);
            _er = er;
            _sync = sync;
        }

        @Override
        public void onSubscribed()
        {
            runInCoreThread_(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    _sync.schedulePull_();
                }
            });
        }

        @Override
        public void onNotificationReceivedFromVerkehr(final String topic,
                @Nullable final byte[] payload)
        {
            assert topic.equals(_topic);
            l.debug("notification received");
            runInCoreThread_(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    _er.retry("syncstatus", new Callable<Void>()
                    {
                        @Override
                        public Void call()
                                throws Exception
                        {
                            _sync.notificationReceived_(PBSyncStatNotification.parseFrom(payload));
                            return null;
                        }
                    });
                }
            });
        }

        @Override
        public void onDisconnected()
        {
            l.warn("disconnected from vk");
            // TODO: sync status is not accurate anymore: make that known to HdGetSyncStatus
        }
    }
}
