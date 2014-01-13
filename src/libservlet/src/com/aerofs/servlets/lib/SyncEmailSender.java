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
    public SyncEmailSender(String host, String port, String username, String password,
            boolean enable_tls, String certificate)
    {
        super(host, port, username, password, enable_tls, certificate);
    }

    @Override
    protected Future<Void> sendMessage(final Message msg, final Session session)
            throws MessagingException
    {
        sendMessageImpl(session, msg);
        return UncancellableFuture.createSucceeded(Void.getDefaultInstance());
    }
}
