/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.C;
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
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import static com.aerofs.base.config.ConfigurationProperties.getBooleanProperty;
import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;

/**
 * This class allows you to send emails (either through local or remote SMTP).
 *
 * TODO (MP) Need to move the sendgrid creds out of here and into hiera...
 * TODO (MP) This class needs some general refactoring love...
 */
public abstract class AbstractEmailSender
{
    private static final Logger l = Loggers.getLogger(AbstractEmailSender.class);

    private final String _host;
    private final String _port;
    private final String _username;
    private final String _password;

    /**
     * @param host when using local mail relay, set this to localhost.
     */
    public AbstractEmailSender(String host, String port, String username, String password)
    {
        _host = host;
        _port = port;
        _username = username;
        _password = password;
    }

    // SMTP command timeout, expressed in milliseconds.
    private static String TIMEOUT =
            getStringProperty("email.sender.timeout", String.valueOf(10 * C.SEC));
    // SMTP connection timeout, expressed in milliseconds.
    private static String CONNECTION_TIMEOUT =
            getStringProperty("email.sender.connection_timeout", String.valueOf(60 * C.SEC));

    private static final String INTERNAL_HOST = "svmail.aerofs.com";
    private static final String INTERNAL_USERNAME = "noreply";
    private static final String INTERNAL_PASSWORD = "qEphE2uzuBr5";

    private static final String CHARSET = "UTF-8";

    public static final Boolean ENABLED =
            getBooleanProperty("lib.notifications.enabled", true);

    public boolean relayIsLocalhost() {
        return _host.equals("localhost");
    }

    public Session getMailSession() {
        Properties props = new Properties();
        props.put("mail.smtp.timeout", TIMEOUT);
        props.put("mail.smtp.connectiontimeout", CONNECTION_TIMEOUT);
        props.put("mail.smtp.port", _port);

        // Without it Java Mail would sene "EHLO" rather thant "EHLO <hostname>". The former
        // is not supported by some mail relays include postfix which is used as the local mail
        // relay for AeroFS Appliance.
        props.put("mail.smtp.localhost", "localhost");

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

    public Future<Void> sendNotificationEmail(String from, @Nullable String fromName,
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
    public Future<Void> sendPublicEmailFromSupport(@Nullable String fromName, String to,
            @Nullable String replyTo, String subject, String textBody, @Nullable String htmlBody,
            @Nonnull EmailCategory category)
            throws MessagingException, UnsupportedEncodingException
    {
        return sendEmail(WWW.SUPPORT_EMAIL_ADDRESS, fromName, to, replyTo, subject, textBody,
                htmlBody, true, category);
    }

    public Future<Void> sendPublicEmail(String from,
            @Nullable String fromName, String to, @Nullable String replyTo, String subject,
            String textBody, @Nullable String htmlBody, @Nonnull EmailCategory category)
            throws MessagingException, UnsupportedEncodingException
    {
        return sendEmail(from, fromName, to, replyTo, subject, textBody, htmlBody, true, category);
    }

    private Future<Void> sendEmail(String from, @Nullable String fromName, String to,
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
            return sendMessage(msg, usingSendGrid, session);
        } catch (RejectedExecutionException e) {
            throw new MessagingException(e.getCause().getMessage());
        }
    }

    private MimeMessage composeMessage(String from, String fromName, String to,
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

    private MimeMultipart createMultipartEmail(String textBody, @Nullable String htmlBody)
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

    protected abstract Future<Void> sendMessage(final Message msg, final boolean publicFacingEmail,
            final Session session)
        throws RejectedExecutionException, MessagingException;

    protected void sendMessageImpl(Session session, boolean publicFacingEmail, Message msg)
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
                        t.connect(_host, _username, _password);
                    }
                } else {
                    t.connect(INTERNAL_HOST, INTERNAL_USERNAME, INTERNAL_PASSWORD);
                }

                l.info("{} emailing {}", msg.getFrom(), msg.getAllRecipients());
                t.sendMessage(msg, msg.getAllRecipients());
            } finally {
                t.close();
            }
        } catch (MessagingException e) {
            l.error("cannot send message: " + Exceptions.getStackTraceAsString(e));
            throw e;
        }
    }
}
