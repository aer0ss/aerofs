package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.ExponentialRetry;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertFilename;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Sp.PBACLNotification;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriberEventListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

import static com.aerofs.lib.Param.Verkehr.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_HOST;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_PORT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_RETRY_INTERVAL;
import static java.util.concurrent.Executors.newCachedThreadPool;

public final class ACLNotificationSubscriber
{
    private static final Logger l = Util.l(ACLNotificationSubscriber.class);

    private final VerkehrSubscriber _sub;

    @Inject
    public ACLNotificationSubscriber(CfgLocalUser localUser, CfgCACertFilename cacert, CoreQueue q,
            CoreScheduler sched, ACLSynchronizer aclsync)
    {
        l.info("creating acl notification subscriber for t:" + localUser.get());
        localUser.get();

        ClientFactory factory = new ClientFactory(VERKEHR_HOST, VERKEHR_PORT,
                newCachedThreadPool(), newCachedThreadPool(), cacert.get(),
                new CfgKeyManagersProvider(), VERKEHR_RETRY_INTERVAL, VERKEHR_ACK_TIMEOUT,
                new HashedWheelTimer(), localUser.get(), new SubscriberEventListener(q, aclsync, sched));

        _sub = factory.create();
    }

    public void start_()
    {
        l.info("started acl notification subscriber");

        _sub.start();
    }

    private static final class SubscriberEventListener implements ISubscriberEventListener
    {
        private static final Logger l = Util.l(SubscriberEventListener.class);

        private final CoreQueue _q;
        private final ACLSynchronizer _aclsync;
        private final ExponentialRetry _er;

        @Inject
        public SubscriberEventListener(CoreQueue q, ACLSynchronizer aclsync, CoreScheduler sched)
        {
            _q = q;
            _aclsync = aclsync;
            _er = new ExponentialRetry(sched);
        }

        @Override
        public void onSubscribed()
        {
            l.info("sel: subscribed - syncing to local");

            _q.enqueueBlocking(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    exponentialRetrySyncToLocal_(new Callable<Void>()
                    {
                        @Override
                        public Void call()
                                throws Exception
                        {
                            _aclsync.syncToLocal_();
                            return null;
                        }
                    });
                }
            }, Prio.LO);
        }

        @Override
        public void onNotificationReceived(String topic, @Nullable final byte[] payload)
        {
            assert topic.equals(Cfg.user()) :
                    "mismatched topic exp:" + Cfg.user() + " act:" + topic;

            l.info("sel: notification received t:" + topic);

            _q.enqueueBlocking(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    exponentialRetrySyncToLocal_(new Callable<Void>()
                    {
                        @Override
                        public Void call()
                                throws Exception
                        {
                            try {
                                long aclEpoch = PBACLNotification.parseFrom(payload).getAclEpoch();
                                _aclsync.syncToLocal_(aclEpoch);
                            } catch (InvalidProtocolBufferException e) {
                                assert false : ("unrecognized pb from verkehr");
                            }

                            return null;
                        }
                    });
                }
            }, Prio.LO);
        }

        private void exponentialRetrySyncToLocal_(Callable<Void> syncToLocalCallable)
        {
            _er.retry("aclsync", syncToLocalCallable);
        }
    }
}
