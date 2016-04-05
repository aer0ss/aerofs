/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.ids.UserID;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.ssmp.*;

import javax.inject.Inject;
import java.util.Collection;

import static com.aerofs.sp.server.LipwigUtil.lipwigFutureGet;

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
}
