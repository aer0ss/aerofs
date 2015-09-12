package com.aerofs.daemon.core.acl;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.ssmp.*;
import com.aerofs.ssmp.SSMPClient.ConnectionListener;
import com.aerofs.ssmp.SSMPRequest.SubscriptionFlag;
import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.addCallback;

public final class ACLNotificationSubscriber implements ConnectionListener, EventHandler
{
    private static final Logger l = Loggers.getLogger(ACLNotificationSubscriber.class);

    private final SSMPIdentifier _topic;
    private final SSMPConnection _ssmp;
    private final Scheduler _sched;
    private final ExponentialRetry _exponential;
    private final ACLSynchronizer _aclSynchronizer;

    @Inject
    public ACLNotificationSubscriber(SSMPConnection ssmp, CfgLocalUser localUser,
            CoreScheduler scheduler, ACLSynchronizer aclSynchronizer)
    {
        _topic = SSMPIdentifier.fromInternal("acl/" + Base64.getEncoder().encodeToString(
                localUser.get().getString().getBytes(StandardCharsets.UTF_8)));
        _ssmp = ssmp;
        _sched = scheduler;
        _exponential = new ExponentialRetry(scheduler);
        _aclSynchronizer = aclSynchronizer;
    }

    public void init_()
    {
        _ssmp.addConnectionListener(this);
        _ssmp.addMcastHandler(_topic.toString(), this);
    }

    private volatile long _aclSyncSeqNum;

    @Override
    public void connected() {
        addCallback(_ssmp.request(SSMPRequest.subscribe(_topic, SubscriptionFlag.NONE)),
                new FutureCallback<SSMPResponse>() {
                    @Override
                    public void onSuccess(@Nullable SSMPResponse r) {
                        if (r.code == SSMPResponse.OK) {
                            launchExpRetryAclSync(null);
                        } else {
                            l.error("failed to acl sub {}", r.code);
                            // TODO: exp retry?
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // TODO: force reconnect?
                    }
                });
    }

    @Override
    public void disconnected() {
        l.warn("disconnected from lipwig");

        // we want to stop on-going exponential retries if the lipwig connection dies
        // before a call to SP succeeds
        ++_aclSyncSeqNum;
    }

    private void launchExpRetryAclSync(@Nullable byte[] payload) {
        final long currentACLSyncSeqNum = ++_aclSyncSeqNum;

        _sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                _exponential.retry("aclsync", () -> {
                    if (currentACLSyncSeqNum != _aclSyncSeqNum) return null;
                    l.debug("sync to local");
                    if (payload != null) {
                        long aclEpoch = Long.valueOf(new String(payload, StandardCharsets.UTF_8));
                        _aclSynchronizer.syncToLocal_(aclEpoch);
                    } else {
                        _aclSynchronizer.syncToLocal_();
                    }
                    return null;
                }, IOException.class);
            }
        }, 0);
    }

    @Override
    public void eventReceived(SSMPEvent e) {
        checkState(_topic.equals(e.to), "topic: act:%s exp:%s", e.to, _topic);

        l.info("recv t:{}", e.to);

        launchExpRetryAclSync(e.payload);
    }
}
