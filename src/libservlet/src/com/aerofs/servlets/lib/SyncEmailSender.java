/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib;

import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.proto.Common.Void;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import java.util.concurrent.Future;

public class SyncEmailSender extends AbstractEmailSender
{
    public SyncEmailSender(String host, String username, String password)
    {
        super(host, username, password);
    }

    @Override
    protected Future<Void> sendMessage(final Message msg, final boolean publicFacingEmail,
            final Session session)
            throws MessagingException
    {
        sendMessageImpl(session, publicFacingEmail, msg);
        return UncancellableFuture.createSucceeded(Void.getDefaultInstance());
    }
}
