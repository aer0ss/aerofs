/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib;

import com.aerofs.base.Loggers;
import com.aerofs.proto.Common.Void;
import org.slf4j.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * The class utilizes a single threaded executor with a queue size of EMAIL_QUEUE_SIZE.
 * If the queue becomes full, the executor throws a runtime RejectedExecutionException.
 */
public class AsyncEmailSender extends AbstractEmailSender
{
    private static final Logger l = Loggers.getLogger(AsyncEmailSender.class);

    private static final int EMAIL_QUEUE_SIZE = 1000;
    private static final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>(EMAIL_QUEUE_SIZE));

    public AsyncEmailSender()
    {
        super(
            getStringProperty("email.sender.public_host", "smtp.sendgrid.net"),
            getStringProperty("email.sender.public_username", "mXSiiSbCMMYVG38E"),
            getStringProperty("email.sender.public_password", "6zovnhQuLMwNJlx8"));
    }

    /**
     * Emails are sent using the executor service which will always send one email at a time.
     * This method is non blocking.
     */
    @Override
    protected Future<Void> sendMessage(final Message msg, final boolean publicFacingEmail,
            final Session session)
            throws RejectedExecutionException
    {
        return executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws MessagingException
            {
                sendMessageImpl(session, publicFacingEmail, msg);
                return null;
            }
        });
    }
}
