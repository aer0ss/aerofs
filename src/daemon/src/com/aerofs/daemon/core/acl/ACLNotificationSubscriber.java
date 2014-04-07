package com.aerofs.daemon.core.acl;

import com.aerofs.base.BaseParam.Topics;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.lib.CoreExecutor;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.proto.SpNotifications.PBACLNotification;
import com.aerofs.verkehr.client.wire.ConnectionListener;
import com.aerofs.verkehr.client.wire.UpdateListener;
import com.aerofs.verkehr.client.wire.VerkehrPubSubClient;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.addCallback;

public final class ACLNotificationSubscriber
{
    private static final Logger l = Loggers.getLogger(ACLNotificationSubscriber.class);

    private final String _topic;
    private final VerkehrPubSubClient _verkehrPubSubClient;
    private final Executor _executor;
    private final ExponentialRetry _exponential;
    private final ACLSynchronizer _aclSynchronizer;

    @Inject
    public ACLNotificationSubscriber(VerkehrPubSubClient verkehrPubSubClient, CfgLocalUser localUser, CoreExecutor executor, CoreScheduler scheduler, ACLSynchronizer aclSynchronizer)
    {
        _topic = Topics.getACLTopic(localUser.get().getString(), false);
        _verkehrPubSubClient = verkehrPubSubClient;
        _executor = executor;
        _exponential = new ExponentialRetry(scheduler);
        _aclSynchronizer = aclSynchronizer;
    }

    public void init_()
    {
        _verkehrPubSubClient.addConnectionListener(new VerkehrListener(), _executor);
    }

    private final class VerkehrListener implements ConnectionListener, UpdateListener
    {
        private long _aclSyncSeqNum;

        @Override
        public void onConnected(VerkehrPubSubClient client)
        {
            addCallback(client.subscribe(_topic, this, _executor), new FutureCallback<Void>()
            {
                @Override
                public void onSuccess(Void aVoid)
                {
                    launchExpRetryAclSync(null);
                }

                @Override
                public void onFailure(Throwable throwable)
                {
                    _verkehrPubSubClient.disconnect();
                }
            }, _executor);
        }

        private void launchExpRetryAclSync(@Nullable final byte[] payload) {

            final long currentACLSyncSeqNum = ++_aclSyncSeqNum;

            _exponential.retry("aclsync", new Callable<Void>()
            {
                @Override
                public Void call()
                        throws Exception
                {
                    if (currentACLSyncSeqNum == _aclSyncSeqNum) {
                        l.debug("sync to local");
                        if (payload != null) {
                            long aclEpoch = PBACLNotification.parseFrom(payload).getAclEpoch();
                            _aclSynchronizer.syncToLocal_(aclEpoch);
                        } else {
                            _aclSynchronizer.syncToLocal_();
                        }
                    } else {
                        l.warn("seqnum mismatch: exp:{} act:{}", currentACLSyncSeqNum, _aclSyncSeqNum);
                    }

                    return null;
                }
            }, IOException.class);
        }

        @Override
        public void onUpdate(final String topic, final byte[] payload)
        {
            checkState(topic.equals(_topic), "topic: act:%s exp:%s", topic, _topic);

            l.info("recv t:{}", topic);

            launchExpRetryAclSync(payload);
        }

        @Override
        public void onDisconnected(VerkehrPubSubClient client)
        {
            l.warn("disconnected from vk");

            // we want to stop on-going exponential retries if the verkehr connection dies
            // before a call to SP succeeds
            ++_aclSyncSeqNum;
        }
    }
}
