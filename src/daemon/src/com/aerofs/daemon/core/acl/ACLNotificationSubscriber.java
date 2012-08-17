package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.verkehr.AbstractVerkehrListener;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.ExponentialRetry;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.Sp.PBACLNotification;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

public final class ACLNotificationSubscriber
{
    private static final Logger l = Util.l(ACLNotificationSubscriber.class);

    private final String _topic;
    private final VerkehrListener _listener;
    private final VerkehrNotificationSubscriber _subscriber;

    @Inject
    public ACLNotificationSubscriber(VerkehrNotificationSubscriber subscriber,
            CfgLocalUser localUser, CoreQueue q, CoreScheduler sched, ACLSynchronizer aclsync)
    {
        _topic = localUser.get();
        _subscriber = subscriber;
        _listener = new VerkehrListener(q, new ExponentialRetry(sched), aclsync);
    }

    public void init_()
    {
        _subscriber.subscribe_(_topic, _listener);
    }

    private final class VerkehrListener extends AbstractVerkehrListener
    {
        private final ExponentialRetry _er;
        private final ACLSynchronizer _aclsync;

        VerkehrListener(CoreQueue q, ExponentialRetry er, ACLSynchronizer aclsync)
        {
            super(q);
            _er = er;
            _aclsync = aclsync;
        }

        @Override
        public void onSubscribed()
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
        public void onNotificationReceived(final String topic, @Nullable final byte[] payload)
        {
            assert topic.equals(_topic);
            l.info("recv notification t:" + topic);
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
                            long aclEpoch = PBACLNotification.parseFrom(payload).getAclEpoch();
                            _aclsync.syncToLocal_(aclEpoch);
                            return null;
                        }
                    });
                }
            });
        }
    }
}
