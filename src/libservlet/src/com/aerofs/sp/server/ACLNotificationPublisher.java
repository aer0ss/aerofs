/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseParam.Topics;
import com.aerofs.ids.UserID;
import com.aerofs.proto.SpNotifications.PBACLNotification;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.verkehr.client.rest.VerkehrClient;
import com.google.common.util.concurrent.ListenableFuture;

import javax.inject.Inject;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

/**
 * Helper class to handle ACL epoch bump and related verkehr notifications
 */
public class ACLNotificationPublisher
{
    private final User.Factory _factUser;
    private final VerkehrClient _vk;

    @Inject
    ACLNotificationPublisher(User.Factory factUser, VerkehrClient vk)
    {
        _factUser = factUser;
        _vk = vk;
    }

    public void publish_(UserID user) throws Exception
    {
        long epoch = _factUser.create(user).incrementACLEpoch();

        PBACLNotification notification = PBACLNotification.newBuilder()
                .setAclEpoch(epoch)
                .build();

        String aclTopic = Topics.getACLTopic(user.getString(), true);
        ListenableFuture<Void> published = _vk.publish(aclTopic, notification.toByteArray());

        verkehrFutureGet_(published);
    }

    public void publish_(Collection<UserID> users) throws Exception
    {
        for (UserID user : users) publish_(user);
    }

    /**
     * Utility to minimize duped code in the below verkehr-related methods.
     * @param future either the verkehr publisher or admin.
     */
    static void verkehrFutureGet_(ListenableFuture<Void> future)
            throws Exception
    {
        try {
            future.get();
        } catch (InterruptedException e) {
            assert false : ("publisher client should never be interrupted");
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof Exception) {
                throw (Exception) e.getCause();
            } else {
                assert false : ("cannot handle arbitrary throwable");
            }
        }
    }
}
