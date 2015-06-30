/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.servlets.lib;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.base.ssl.StringBasedCertificateProvider;
import com.aerofs.proto.Common.Void;
import com.aerofs.sp.server.lib.SPParam;
import com.google.common.base.Strings;
import org.slf4j.Logger;

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
 */
public abstract class AbstractEmailSender
{
    private static final Logger l = Loggers.getLogger(AbstractEmailSender.class);

    private final String _host;
    private final String _port;
    private final String _username;
    private final String _password;
    private final boolean _enable_tls;
    private final @Nullable String _certificate;

    /**
     * @param host when using local mail relay, set this to localhost.
     * @param certificate explicitly-configured certificate (optional, used for self-signed certs)
     */
    public AbstractEmailSender(String host, String port, String username, String password,
            boolean enable_tls, @Nullable String certificate)
    {
        _host = host;
        _port = port;
        _username = username;
        _password = password;
        _enable_tls = enable_tls;
        _certificate = certificate;
    }

    // SMTP command timeout, expressed in milliseconds.
    private static String TIMEOUT =
            getStringProperty("email.sender.timeout", String.valueOf(10 * C.SEC));
    // SMTP connection timeout, expressed in milliseconds.
    private static String CONNECTION_TIMEOUT =
            getStringProperty("email.sender.connection_timeout", String.valueOf(60 * C.SEC));

    private static final String CHARSET = "UTF-8";

    public static final Boolean ENABLED =
            getBooleanProperty("lib.notifications.enabled", true);

    private boolean relayIsLocalhost() {
        return _host.equals("localhost");
    }

    private boolean isEmptyCredentials() {
        return _username.equals("") && _password.equals("");
    }

    public Session getMailSession() throws MessagingException
    {
        Properties props = new Properties();

        if (l.isDebugEnabled()) {
            // N.B. these log lines will end up in /var/log/tomcat6/catalina.out
            props.put("mail.debug", "true");
        }
        props.put("mail.smtp.timeout", TIMEOUT);
        props.put("mail.smtp.connectiontimeout", CONNECTION_TIMEOUT);
        props.put("mail.smtp.port", _port);

        // Without it Java Mail would send "EHLO" rather thant "EHLO <hostname>". The former
        // is not supported by some mail relays include postfix which is used as the local mail
        // relay for AeroFS Appliance.
        props.put("mail.smtp.localhost", "localhost");

        if (relayIsLocalhost()) {
            props.put("mail.smtp.host", "127.0.0.1");
            props.put("mail.smtp.auth", "false");
            props.put("mail.smtp.starttls.enable",   "false");
            props.put("mail.smtp.starttls.required", "false");
        } else {
            props.put("mail.smtp.starttls.enable",   Boolean.toString(_enable_tls));
            props.put("mail.smtp.starttls.required", Boolean.toString(_enable_tls));
            props.put("mail.smtp.host", _host);
            props.put("mail.smtp.auth", Boolean.toString(!isEmptyCredentials()));
            if (_enable_tls && !Strings.isNullOrEmpty(_certificate)) {
                // explicitly-configured certificate, used for self-signed or otherwise
                // unliked-by-javax-mail servers.
                try {
                    // SSLEngineFactory is a convenient path to building the TrustManager;
                    // it will wrap access to the normal X509 path plus allow the explicit cert
                    SSLEngineFactory factory = new SSLEngineFactory(Mode.Client, Platform.Desktop,
                            null, new StringBasedCertificateProvider(_certificate), null);
                    props.put("mail.smtp.ssl.socketFactory",
                            factory.getSSLContext().getSocketFactory());
                } catch (Exception e) {
                    l.warn("Failed creating a custom SSL Socket Factory", e);
                    throw new MessagingException(e.getMessage());
                }
            }
        }

        return Session.getInstance(props);
    }

    /**
     *  Deprecated.  Identical to sendEmail(), but will noop if notifications are disabled.
     */
    public Future<Void> sendDeprecatedNotificationEmail(String from, @Nullable String fromName,
            String to, @Nullable String replyTo, String subject, String textBody,
            @Nullable String htmlBody)
            throws MessagingException, UnsupportedEncodingException
    {
        // If notifications are disabled, this is a noop.
        if (!ENABLED) {
            return UncancellableFuture.createSucceeded(Void.getDefaultInstance());
        }

        return sendEmail(from, fromName, to, replyTo, subject, textBody, htmlBody);
    }

    /**
     * Similar to sendPublicEmail(), except that it always uses WWW.SUPPORT_EMAIL_ADDRESS as the
     * from address.
     */
    public Future<Void> sendPublicEmailFromSupport(@Nullable String fromName, String to,
            @Nullable String replyTo, String subject, String textBody, @Nullable String htmlBody)
            throws MessagingException, UnsupportedEncodingException
    {
        return sendEmail(WWW.SUPPORT_EMAIL_ADDRESS, fromName, to, replyTo, subject, textBody,
                htmlBody);
    }

    public Future<Void> sendPublicEmail(String from, @Nullable String fromName, String to, @Nullable String replyTo, String subject,
            String textBody, @Nullable String htmlBody)
            throws MessagingException, UnsupportedEncodingException
    {
        return sendEmail(from, fromName, to, replyTo, subject, textBody, htmlBody);
    }

    private Future<Void> sendEmail(String from, @Nullable String fromName, String to,
            @Nullable String replyTo, String subject, String textBody, @Nullable String htmlBody)
            throws MessagingException, UnsupportedEncodingException
    {
        MimeMessage msg;
        MimeMultipart multiPart = createMultipartEmail(textBody, htmlBody);
        Session session = getMailSession();
        msg = composeMessage(from,
                (fromName == null) ? SPParam.BRAND : fromName,
                to,
                replyTo,
                subject,
                session);
        msg.setContent(multiPart);

        try {
            return sendMessage(msg, session);
        } catch (RejectedExecutionException e) {
            throw new MessagingException(e.getCause().getMessage());
        }
    }

    private MimeMessage composeMessage(String from, String fromName, String to,
            @Nullable String replyTo, String subject, Session sess)
            throws MessagingException, UnsupportedEncodingException
    {
        MimeMessage msg = new MimeMessage(sess);

        msg.setFrom(new InternetAddress(from, fromName, CHARSET));
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

    protected abstract Future<Void> sendMessage(final Message msg, final Session session)
        throws RejectedExecutionException, MessagingException;

    protected void sendMessageImpl(Session session, Message msg)
            throws MessagingException
    {
        try {
            Transport t = session.getTransport("smtp");
            try {
                if (session.getProperty("mail.smtp.auth").equals("true")) {
                    t.connect(_username, _password);
                } else {
                    t.connect();
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
