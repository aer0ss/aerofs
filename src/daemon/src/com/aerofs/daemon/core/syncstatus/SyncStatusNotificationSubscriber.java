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
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriptionListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

import static com.aerofs.lib.Param.Verkehr.VERKEHR_HOST;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_PORT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_RETRY_INTERVAL;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * This class connects to verkher and subscribe to a specific channel through which the sync stat
 * server will push notifications whenever the sync status of an object we share is modified.
 */
public class SyncStatusNotificationSubscriber
{
    private static final Logger l = Util.l(SyncStatusNotificationSubscriber.class);

    private final String _topic;
    private final VerkehrSubscriber _subscriber;
    private final CoreQueue _q;

    @Inject
    public SyncStatusNotificationSubscriber(CfgLocalUser localUser, CfgLocalDID localDID,
            CfgCACertFilename cacert, CoreQueue q, CoreScheduler sched, SyncStatusSynchronizer sync)
    {
        this._q = q;

        VerkehrListener listener = new VerkehrListener(sync, new ExponentialRetry(sched));

        ClientFactory factory = new ClientFactory(VERKEHR_HOST, VERKEHR_PORT,
                newCachedThreadPool(), newCachedThreadPool(),
                cacert.get(), new CfgKeyManagersProvider(),
                VERKEHR_RETRY_INTERVAL, Cfg.db().getLong(Key.TIMEOUT), new HashedWheelTimer(),
                listener, listener, sameThreadExecutor());

        this._topic = localDID.get().toStringFormal() + localUser.get();
        this._subscriber = factory.create();
    }

    public void start_()
    {
        l.info("started sync status notification subscriber");
        _subscriber.start();
    }

    private final class VerkehrListener implements IConnectionListener, ISubscriptionListener
    {
        private final SyncStatusSynchronizer _sync;
        private final ExponentialRetry _er;

        @Inject
        public VerkehrListener(SyncStatusSynchronizer sync, ExponentialRetry er)
        {
            this._sync = sync;
            this._er = er;
        }

        @Override
        public void onConnected()
        {
            _subscriber.subscribe_(_topic);
        }

        @Override
        public void onNotificationReceived(final String topic, @Nullable final byte[] payload)
        {
            assert topic.equals(_topic);

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
            // noop - the client auto-reconnects for you
        }

        private void runInCoreThread_(AbstractEBSelfHandling event)
        {
            _q.enqueueBlocking(event, Prio.LO);
        }
    }
}
