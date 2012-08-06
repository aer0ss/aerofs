package com.aerofs.daemon.core.syncstatus;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.ExponentialRetry;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertFilename;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Sp.PBSyncStatNotification;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriberEventListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

import static com.aerofs.lib.Param.Verkehr.VERKEHR_HOST;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_PORT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_RETRY_INTERVAL;
import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * This class connects to verkher and subscribe to a specific channel through which the sync stat
 * server will push notifications whenever the sync status of an object we share is modified.
 */
public class SyncStatusNotificationSubscriber {
    private static final Logger l = Util.l(SyncStatusNotificationSubscriber.class);

    private final VerkehrSubscriber _sub;
    private final String _topic;

    @Inject
    public SyncStatusNotificationSubscriber(CfgLocalUser localUser, CfgLocalDID localDID,
            CfgCACertFilename cacert, CoreQueue q, CoreScheduler sched, SyncStatusSynchronizer sync)
    {
        _topic = localDID.get().toStringFormal() + localUser.get();
        l.info("creating sync status notification subscriber for t:" + _topic);

        ClientFactory factory = new ClientFactory(VERKEHR_HOST, VERKEHR_PORT, newCachedThreadPool(),
                newCachedThreadPool(), cacert.get(), new CfgKeyManagersProvider(),
                VERKEHR_RETRY_INTERVAL, Cfg.db().getLong(Key.TIMEOUT), new HashedWheelTimer(),
                _topic, new SubscriberEventListener(q, sched, sync, _topic));

        _sub = factory.create();
    }

    public void start_()
    {
        l.info("started sync status notification subscriber");

        _sub.start();
    }

    private static final class SubscriberEventListener implements ISubscriberEventListener
    {
        private static final Logger l = Util.l(SubscriberEventListener.class);

        private final CoreQueue _q;
        private final ExponentialRetry _er;
        private final SyncStatusSynchronizer _sync;
        private final String _topic;

        @Inject
        public SubscriberEventListener(CoreQueue q, CoreScheduler sched,
                SyncStatusSynchronizer sync, String topic)
        {
            _q = q;
            _er = new ExponentialRetry(sched);
            _sync = sync;
            _topic = topic;
        }

        @Override
        public void onSubscribed()
        {
            l.info("sel: subscribed");
        }

        @Override
        public void onNotificationReceived(String topic, @Nullable final byte[] payload)
        {
            assert topic.equals(_topic) : _topic + " : " + topic;

            l.info("sel: notification received t:" + topic);

            _q.enqueueBlocking(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    _er.retry("syncstatus", new Callable<Void>()
                    {
                        @Override
                        public Void call() throws Exception
                        {
                            _sync.notificationReceived_(PBSyncStatNotification.parseFrom(payload));
                            return null;
                        }
                    });
                }
            }, Prio.LO);
        }
    }
}
