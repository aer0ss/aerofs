/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.Loggers;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.labeling.L;
import com.aerofs.proto.Common.Void;
import com.aerofs.sv.common.EmailCategory;
import org.slf4j.Logger;

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

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

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

    private static String PUBLIC_HOST =
            getStringProperty("email.sender.public_host", "smtp.sendgrid.net");
    private static String PUBLIC_USERNAME =
            getStringProperty("email.sender.public_username", "mXSiiSbCMMYVG38E");
    private static String PUBLIC_PASSWORD =
            getStringProperty("email.sender.public_password", "6zovnhQuLMwNJlx8");

    private static final String INTERNAL_HOST = "svmail.aerofs.com";
    private static final String INTERNAL_USERNAME = "noreply";
    private static final String INTERNAL_PASSWORD = "qEphE2uzuBr5";

    private static final String CHARSET = "UTF-8";

    public static final Boolean ENABLED =
            getBooleanProperty("lib.notifications.enabled", true);

    public static boolean relayIsLocalhost() {
        return PUBLIC_HOST.equals("localhost");
    }

    public static Session getMailSession() {
        Properties props = new Properties();
        if (relayIsLocalhost()) {
            props.put("mail.stmp.host", "127.0.0.1");
            props.put("mail.stmp.auth", "false");
        } else {
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable","true");
            props.put("mail.smtp.starttls.required", "true");
        }

        return Session.getInstance(props);
    }

    public static Future<Void> sendNotificationEmail(String from, @Nullable String fromName,
            String to, @Nullable String replyTo, String subject, String textBody,
            @Nullable String htmlBody)
            throws MessagingException, UnsupportedEncodingException
    {
        // If notifications are disabled, this is a noop.
        if (!ENABLED) {
            return UncancellableFuture.createSucceeded(Void.getDefaultInstance());
        }

        return sendEmail(from, fromName, to, replyTo, subject, textBody, htmlBody, false, null);
    }

    /**
     * Similar to sendPublicEmail(), except that it always uses WWW.SUPPORT_EMAIL_ADDRESS as the
     * from address.
     */
    public static Future<Void> sendPublicEmailFromSupport(@Nullable String fromName, String to,
            @Nullable String replyTo, String subject, String textBody, @Nullable String htmlBody,
            @Nonnull EmailCategory category)
            throws MessagingException, UnsupportedEncodingException
    {
        return sendEmail(WWW.SUPPORT_EMAIL_ADDRESS, fromName, to, replyTo, subject, textBody,
                htmlBody, true, category);
    }

    public static Future<Void> sendPublicEmail(String from,
            @Nullable String fromName, String to, @Nullable String replyTo, String subject,
            String textBody, @Nullable String htmlBody, @Nonnull EmailCategory category)
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
        Session session = getMailSession();
        msg = composeMessage(from,
                (fromName == null) ? L.brand() : fromName,
                to,
                replyTo,
                subject,
                session);
        msg.setContent(multiPart);

        if (category != null) {
            msg.addHeaderLine("X-SMTPAPI: {\"category\": \"" + category.name() + "\"}");
        }

        try {
            return sendEmail(msg, usingSendGrid, session);
        } catch (RejectedExecutionException e) {
            throw new MessagingException(e.getCause().getMessage());
        }
    }

    private static MimeMessage composeMessage(String from, String fromName, String to,
            @Nullable String replyTo, String subject, Session sess)
            throws MessagingException, UnsupportedEncodingException
    {
        MimeMessage msg = new MimeMessage(sess);

        msg.setFrom(new InternetAddress(from, fromName));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        msg.setSubject(subject, CHARSET);

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
    private static Future<Void> sendEmail(final Message msg, final boolean publicFacingEmail,
            final Session session)
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
                    // We use SendGrid for any publically facing emails (like signup codes, device
                    // certification notifications, etc.), and our own mail servers for internal
                    // emails (for notifying us that a user has signed up, shared a folder, etc.)
                    try {
                        if (publicFacingEmail) {
                            if (relayIsLocalhost()) {
                                // localhost does not require authentication.
                                // In fact, it generally requires the lack thereof.
                                t.connect();
                            } else {
                                t.connect(PUBLIC_HOST, PUBLIC_USERNAME, PUBLIC_PASSWORD);
                            }
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
                        l.error(Exceptions.getStackTraceAsString(e));
                    } catch (Exception e1) {
                        l.error("cannot report email exception: {} ", e1);
                    }

                    throw e;
                }
            }
        });
    }
}
