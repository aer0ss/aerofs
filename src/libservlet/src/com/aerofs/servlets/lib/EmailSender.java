/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.labeling.L;
import com.aerofs.sv.common.EmailCategory;
import com.netflix.config.DynamicBooleanProperty;
import org.slf4j.Logger;

import com.aerofs.proto.Common.Void;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The EmailSender class allows you to send emails (either through local or remote SMTP).
 *
 * The class utilizes a single threaded executor with a queue size of EMAIL_QUEUE_SIZE.
 * If the queue becomes full, the executor throws a runtime RejectedExecutionException
 */
public class EmailSender
{
    private static final Logger l = Loggers.getLogger(EmailSender.class);

    private static final int EMAIL_QUEUE_SIZE = 1000;
    private static final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingDeque<Runnable>(EMAIL_QUEUE_SIZE));

    private static final String PUBLIC_HOST = "smtp.sendgrid.net";
    private static final String PUBLIC_USERNAME = "mXSiiSbCMMYVG38E";
    private static final String PUBLIC_PASSWORD = "6zovnhQuLMwNJlx8";
    private static final String INTERNAL_HOST = "svmail.aerofs.com";
    private static final String INTERNAL_USERNAME = "noreply";
    private static final String INTERNAL_PASSWORD = "qEphE2uzuBr5";
    private static Session session = null;

    private static final String CHARSET = "UTF-8";

    public static final DynamicBooleanProperty ENABLED =
            new DynamicBooleanProperty("lib.notifications.enabled", true);

    static {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable","true");
        props.put("mail.smtp.startls.required", "true");

        session = Session.getInstance(props);
    }

    public static Future<Void> sendNotificationEmail(String from, @Nullable String fromName,
            String to, @Nullable String replyTo, String subject, String textBody,
            @Nullable String htmlBody)
            throws MessagingException, UnsupportedEncodingException
    {
        // If notifications are disabled, this is a noop.
        if (!ENABLED.get()) {
            return UncancellableFuture.createSucceeded(Void.getDefaultInstance());
        }

        return sendEmail(from, fromName, to, replyTo, subject, textBody, htmlBody, false, null);
    }

    public static Future<Void> sendPublicEmail(String from, @Nullable String fromName, String to,
            @Nullable String replyTo, String subject, String textBody, @Nullable String htmlBody,
            @Nonnull EmailCategory category)
            throws MessagingException, UnsupportedEncodingException
    {
        return sendEmail(from, fromName, to, replyTo, subject, textBody, htmlBody, true, category);
    }

    private static Future<Void> sendEmail(String from, @Nullable String fromName, String to,
            @Nullable String replyTo, String subject, String textBody, @Nullable String htmlBody,
            boolean usingSendGrid, @Nullable EmailCategory category)
            throws MessagingException, UnsupportedEncodingException
    {
        assert !usingSendGrid || category != null;

        MimeMessage msg;
        MimeMultipart multiPart = createMultipartEmail(textBody, htmlBody);
        msg = composeMessage(from,
                (fromName == null) ? L.brand() : fromName,
                to,
                replyTo,
                subject);
        msg.setContent(multiPart);

        if (category != null) {
            msg.addHeaderLine("X-SMTPAPI: {\"category\": \"" + category.name() + "\"}");
        }

        try {
            return sendEmail(msg, usingSendGrid);
        } catch (RejectedExecutionException e) {
            throw new MessagingException(e.getCause().getMessage());
        }
    }

    private static MimeMessage composeMessage(String from, String fromName, String to,
            @Nullable String replyTo, String subject)
            throws MessagingException, UnsupportedEncodingException
    {
        MimeMessage msg = new MimeMessage(session);

        msg.setFrom(new InternetAddress(from, fromName));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        if (L.isStaging()) {
            msg.setSubject("[STAGING] " + subject, CHARSET);
        } else {
            msg.setSubject(subject, CHARSET);
        }

        if (replyTo != null) msg.setReplyTo(InternetAddress.parse(replyTo, false));

        return msg;
    }

    private static MimeMultipart createMultipartEmail(String textBody, @Nullable String htmlBody)
            throws MessagingException
    {
        MimeMultipart multiPart = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();

        textPart.setContent(textBody, "text/plain; charset=\"" + CHARSET +
                "\"");

        multiPart.addBodyPart(textPart);

        if (htmlBody != null) {
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=\"" + CHARSET + "\"");
            multiPart.addBodyPart(htmlPart);
        }


        return multiPart;
    }

    /**
     * Emails are sent using the executor service which will always send one email at a time.
     * This method is non blocking.
     */
    private static Future<Void> sendEmail(final Message msg, final boolean usingSendGrid)
        throws RejectedExecutionException
    {
        return executor.submit(new Callable<Void>()
        {
            @Override
            public Void call()
                    throws MessagingException
            {
                try {
                    Transport t = session.getTransport("smtp");
                    // We use SendGrid for any publically facing emails, and our own mail servers
                    // for internal emails.
                    try {
                        if (usingSendGrid) {
                            t.connect(PUBLIC_HOST, PUBLIC_USERNAME, PUBLIC_PASSWORD);
                        } else {
                            t.connect(INTERNAL_HOST, INTERNAL_USERNAME, INTERNAL_PASSWORD);
                        }

                        t.sendMessage(msg, msg.getAllRecipients());
                        l.info("{} emailed {}", msg.getFrom(), msg.getAllRecipients());
                    } finally {
                        t.close();
                    }
                    return null;
                } catch (MessagingException e) {
                    try {
                        String to = msg.getRecipients(Message.RecipientType.TO)[0].toString();
                        l.error("cannot send message to {} : {} ", to , e);
                    } catch (Exception e1) {
                        l.error("cannot report email exception: {} ", e1);
                    }

                    throw e;
                }
            }
        });
    }
}
