package com.aerofs.daemon.core.acl;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.verkehr.AbstractVerkehrListener;
import com.aerofs.daemon.core.verkehr.VerkehrNotificationSubscriber;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.proto.SpNotifications.PBACLNotification;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
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
        _topic = Param.ACL_CHANNEL_TOPIC_PREFIX + localUser.get().toString();
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

        private long _aclSyncSeqNum;

        VerkehrListener(CoreQueue q, ExponentialRetry er, ACLSynchronizer aclsync)
        {
            super(q);

            this._er = er;
            this._aclsync = aclsync;
        }

        @Override
        public void onSubscribed()
        {
            launchExpRetryAclSync(null);
        }

        private void launchExpRetryAclSync(@Nullable final byte[] payload) {

            final long currentACLSyncSeqNum = ++_aclSyncSeqNum;

            runInCoreThread_(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    _er.retry("aclsync", new Callable<Void>() {
                        @Override
                        public Void call() throws Exception
                        {
                            if (currentACLSyncSeqNum == _aclSyncSeqNum) {
                                l.debug("sync to local");
                                if (payload != null) {
                                    long aclEpoch = PBACLNotification.parseFrom(payload).getAclEpoch();
                                    _aclsync.syncToLocal_(aclEpoch);
                                } else {
                                    _aclsync.syncToLocal_();
                                }
                            } else {
                                l.warn("seqnum mismatch: "
                                        + "exp:" + currentACLSyncSeqNum + " act:" + _aclSyncSeqNum);
                            }

                            return null;
                        }
                    }, IOException.class);
                };
            });
        }

        @Override
        public void onNotificationReceivedFromVerkehr(final String topic,
                @Nullable final byte[] payload)
        {
            assert topic.equals(_topic);

            l.info("recv t:" + topic);
            launchExpRetryAclSync(payload);
        }

        @Override
        public void onDisconnected()
        {
            l.warn("disconnected from vk");

            runInCoreThread_(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    // we want to stop on-going exponential retries if the verkehr connection dies
                    // before a call to SP succeeds
                    ++_aclSyncSeqNum;
                }
            });
        }
    }
}
