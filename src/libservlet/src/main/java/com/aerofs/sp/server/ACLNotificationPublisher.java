/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.UserID;
import com.aerofs.servlets.lib.db.sql.SQLThreadLocalTransaction;
import com.aerofs.sp.server.lib.user.User;
import com.aerofs.ssmp.SSMPConnection;
import com.aerofs.ssmp.SSMPIdentifier;
import com.aerofs.ssmp.SSMPIdentifiers;
import com.aerofs.ssmp.SSMPRequest;

import org.slf4j.Logger;

import javax.inject.Inject;

import java.util.Collection;

import static com.aerofs.sp.server.LipwigUtil.lipwigFutureGet;

/**
 * Helper class to handle ACL epoch bump and related lipwig notifications
 */
public class ACLNotificationPublisher
{
    private final static Logger l = Loggers.getLogger(ACLNotificationPublisher.class);

    private final User.Factory _factUser;
    private final SSMPConnection _ssmp;
    private final SQLThreadLocalTransaction _sqlTrans;

    @Inject
    public ACLNotificationPublisher(User.Factory factUser, SSMPConnection ssmp,
                                    SQLThreadLocalTransaction sqlTrans)
    {
        _factUser = factUser;
        _ssmp = ssmp;
        _sqlTrans = sqlTrans;
    }

    public void publish_(UserID user) throws Exception
    {
        long epoch = _factUser.create(user).incrementACLEpoch();

        SSMPIdentifier aclTopic = SSMPIdentifiers.getACLTopic(user.getString());
        Runnable publish = () -> {
            try {
                lipwigFutureGet(_ssmp.request(SSMPRequest.mcast(aclTopic, Long.toString(epoch))));
            } catch (Exception e) {
                l.warn("acl pub failed {}:{}", user, epoch, BaseLogUtil.suppress(e));
            }
        };

        // schedule publish on commit if we're in a transaction, otherwise publish now
        if (_sqlTrans.isInTransaction()) {
            _sqlTrans.onCommit(publish);
        } else {
            publish.run();
        }

    }

    public void publish_(Collection<UserID> users) throws Exception
    {
        // TODO: pipeline requests
        for (UserID user : users) publish_(user);
    }

}
