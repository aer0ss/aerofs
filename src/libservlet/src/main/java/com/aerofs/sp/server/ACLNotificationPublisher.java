/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.ids.UserID;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.ssmp.*;
import com.google.common.util.concurrent.ListenableFuture;

import javax.inject.Inject;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

/**
 * Helper class to handle ACL epoch bump and related lipwig notifications
 */
public class ACLNotificationPublisher
{
    private final User.Factory _factUser;
    private final SSMPConnection _ssmp;

    @Inject
    public ACLNotificationPublisher(User.Factory factUser, SSMPConnection ssmp)
    {
        _factUser = factUser;
        _ssmp = ssmp;
    }

    public void publish_(UserID user) throws Exception
    {
        long epoch = _factUser.create(user).incrementACLEpoch();

        SSMPIdentifier aclTopic = SSMPIdentifiers.getACLTopic(user.getString());
        lipwigFutureGet(_ssmp.request(SSMPRequest.mcast(aclTopic, Long.toString(epoch))));
    }

    public void publish_(Collection<UserID> users) throws Exception
    {
        // TODO: pipeline requests
        for (UserID user : users) publish_(user);
    }

    /**
     * Utility to minimize duped code in the below lipwig-related methods.
     */
    private static void lipwigFutureGet(ListenableFuture<SSMPResponse> future)
            throws Exception
    {
        try {
            SSMPResponse r = future.get();
            if (r.code == SSMPResponse.NOT_FOUND) {
                // NB: UCAST will 404 if the user is not connected
                // NB: MCAST will 404 if no user subscribed to the topic
            } else if (r.code != SSMPResponse.OK) {
                throw new Exception("unexpected response " + r.code);
            }
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
