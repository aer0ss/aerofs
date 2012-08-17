package com.aerofs.daemon.core.acl;

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
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Sp.PBACLNotification;
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriptionListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

import static com.aerofs.lib.Param.Verkehr.VERKEHR_HOST;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_PORT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_RETRY_INTERVAL;
import static com.google.common.util.concurrent.Futures.addCallback;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.util.concurrent.Executors.newCachedThreadPool;

public final class ACLNotificationSubscriber
{
    private static final Logger l = Util.l(ACLNotificationSubscriber.class);

    private final String _topic;
    private final VerkehrSubscriber _subscriber;
    private final CoreQueue _q;

    @Inject
    public ACLNotificationSubscriber(CfgLocalUser localUser, CfgCACertFilename cacert, CoreQueue q,
            CoreScheduler sched, ACLSynchronizer aclsync)
    {
        this._q = q;
        this._topic = localUser.get();

        VerkehrListener listener = new VerkehrListener(aclsync, new ExponentialRetry(sched));

        ClientFactory factory = new ClientFactory(VERKEHR_HOST, VERKEHR_PORT,
                newCachedThreadPool(), newCachedThreadPool(),
                cacert.get(), new CfgKeyManagersProvider(),
                VERKEHR_RETRY_INTERVAL, Cfg.db().getLong(Key.TIMEOUT) , new HashedWheelTimer(),
                listener, listener, sameThreadExecutor());

        this._subscriber = factory.create();
    }

    public void start_()
    {
        l.info("started acl notification subscriber");
        _subscriber.start();
    }

    private final class VerkehrListener implements IConnectionListener, ISubscriptionListener
    {
        private final ACLSynchronizer _aclsync;
        private final ExponentialRetry _er;

        @Inject
        public VerkehrListener(ACLSynchronizer aclsync, ExponentialRetry er)
        {
            _aclsync = aclsync;
            _er = er;
        }

        @Override
        public void onConnected()
        {
            addCallback(_subscriber.subscribe_(_topic), new FutureCallback<Void>()
            {
                @Override
                public void onSuccess(Void v)
                {
                    runInCoreThread_(new AbstractEBSelfHandling()
                    {
                        @Override
                        public void handle_()
                        {
                            _er.retry("aclsync", new Callable<Void>()
                            {
                                @Override
                                public Void call()
                                        throws Exception
                                {
                                    l.info("sync to local");
                                    _aclsync.syncToLocal_();
                                    return null;
                                }
                            });
                        }
                    });
                }

                @Override
                public void onFailure(Throwable t)
                {
                    l.warn("fail subscribe t:" + _topic);
                }
            });
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
                    _er.retry("aclsync", new Callable<Void>()
                    {
                        @Override
                        public Void call()
                                throws Exception
                        {
                            l.info("recv notification t:" + topic);
                            long aclEpoch = PBACLNotification.parseFrom(payload).getAclEpoch();
                            _aclsync.syncToLocal_(aclEpoch);
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
