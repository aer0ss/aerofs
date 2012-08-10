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
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

import static com.aerofs.lib.Param.Verkehr.VERKEHR_HOST;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_PORT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_RETRY_INTERVAL;
import static java.util.concurrent.Executors.newCachedThreadPool;

public class SyncStatusNotificationSubscriber {
    private static final Logger l = Util.l(SyncStatusNotificationSubscriber.class);

    private final VerkehrSubscriber _sub;

    @Inject
    public SyncStatusNotificationSubscriber(CfgLocalUser localUser, CfgLocalDID localDID,
                                            CfgCACertFilename cacert, CoreQueue q,
                                            CoreScheduler sched, SyncStatusSynchronizer sync)
    {
        l.info("creating sync status notification subscriber for t:" + localUser.get());

        ClientFactory factory = new ClientFactory(VERKEHR_HOST, VERKEHR_PORT, newCachedThreadPool(),
                newCachedThreadPool(), cacert.get(), new CfgKeyManagersProvider(),
                VERKEHR_RETRY_INTERVAL, Cfg.db().getLong(Key.TIMEOUT), new HashedWheelTimer(),
                localDID.get() + localUser.get(), new SubscriberEventListener(q, sync, sched));

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

        @Inject
        public SubscriberEventListener(CoreQueue q, SyncStatusSynchronizer sync, CoreScheduler sched)
        {
            _q = q;
            _er = new ExponentialRetry(sched);
            _sync = sync;
        }

        @Override
        public void onSubscribed()
        {
            l.info("sel: subscribed");
        }

        @Override
        public void onNotificationReceived(String topic, @Nullable final byte[] payload)
        {
            // TODO(huguesb): confirm topic format
            assert topic.equals(Cfg.did() + Cfg.user()) :
                    "mismatched topic exp:" + Cfg.did() + Cfg.user() + " act:" + topic;

            l.info("sel: notification received t:" + topic);

            _q.enqueueBlocking(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    exponentialRetry_(new Callable<Void>()
                    {
                        @Override
                        public Void call()
                                throws Exception
                        {
                            try {
                                long syncEpoch = PBSyncStatNotification.parseFrom(payload).getSsEpoch();
                                _sync.checkEpoch_(syncEpoch);
                            } catch (InvalidProtocolBufferException e) {
                                assert false : ("unrecognized pb from verkehr");
                            }

                            return null;
                        }
                    });
                }
            }, Prio.LO);
        }

        private void exponentialRetry_(Callable<Void> callable)
        {
            _er.retry("syncstatus", callable);
        }
    }
}
