/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib;

import com.aerofs.proto.Common.Void;

import javax.mail.Message;
import javax.mail.Session;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * The class utilizes a single threaded executor with a queue size of EMAIL_QUEUE_SIZE.
 * If the queue becomes full, the executor throws a runtime RejectedExecutionException.
 * To instantiate this class, see AsyncEmailSender#create()
 */
public class AsyncEmailSender extends AbstractEmailSender
{
    private final int EMAIL_QUEUE_SIZE = 1000;
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(EMAIL_QUEUE_SIZE));

    private AsyncEmailSender(String host, String port, String username, String password,
            boolean useTls, String certificate)
    {
        super(host, port, username, password, useTls, certificate);
    }

    /**
     * Creates an AsyncEmailSender configured to send emails to customers.
     */
    public static AsyncEmailSender create()
    {
        // Note that the config server overrides local credentials, and that at
        // least one of (config server, local creds) must be present.
        // We wish there was no default value here, so we could fail loudly if no production
        // config is found; however this breaks the development environment.
        String host = getStringProperty("email.sender.public_host", null);
        String port = getStringProperty("email.sender.public_port", null);
        String username = getStringProperty("email.sender.public_username", null);
        String password = getStringProperty("email.sender.public_password", null);
        boolean useTls = getBooleanProperty("email.sender.public_enable_tls", true);
        String cert = getStringProperty("email.sender.public_cert", null);

        return new AsyncEmailSender(host, port, username, password, useTls, cert);
    }

    /**
     * Emails are sent using the executor service which will always send one email at a time.
     * This method is non blocking.
     */
    @Override
    protected Future<Void> sendMessage(final Message msg, final Session session)
            throws RejectedExecutionException
    {
        return executor.submit(() -> {
            sendMessageImpl(session, msg);
            return null;
        });
    }
}
