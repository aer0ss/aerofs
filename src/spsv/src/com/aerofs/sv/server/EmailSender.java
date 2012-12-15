/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sv.server;

import com.aerofs.lib.L;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.sv.common.EmailCategory;
import org.apache.log4j.Logger;

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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class EmailSender
{
    private static final Logger l = com.aerofs.lib.Util.l(EmailSender.class);

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    protected static final String SENDGRID_HOST = "smtp.sendgrid.net";
    protected static final String SENDGRID_USERNAME = "mXSiiSbCMMYVG38E";
    protected static final String SENDGRID_PASSWORD = "6zovnhQuLMwNJlx8";
    private static final String LOCAL_HOST = "svmail.aerofs.com";
    private static final String LOCAL_USERNAME = "noreply";
    private static final String LOCAL_PASSWORD = "qEphE2uzuBr5";
    private static Session session = null;

    private static final String CHARSET = "UTF-8";

    static {
        Properties props = System.getProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable","true");
        props.put("mail.smtp.startls.required", "true");

        session = Session.getInstance(System.getProperties());
    }


    public static Future<Void> sendEmail(String from, @Nullable String fromName, String to,
            @Nullable String replyTo, String subject, String textBody, @Nullable String htmlBody,
            boolean usingSendGrid, @Nullable EmailCategory category)
            throws MessagingException, UnsupportedEncodingException
    {
        assert !usingSendGrid || category != null;

        MimeMessage msg;
        MimeMultipart multiPart = createMultipartEmail(textBody, htmlBody);
        msg = composeMessage(from,
                (fromName == null) ? L.PRODUCT : fromName,
                to,
                replyTo,
                subject);
        msg.setContent(multiPart);

        if (category != null) msg.addHeaderLine("X-SMTPAPI: {\"category\": \"" + category.name() + "\"}");

        return sendEmail(msg, usingSendGrid);
    }

    /*
     * @param replyTo may be null
     * @param body may be null
     */
    private static MimeMessage composeMessage(String from, String fromName, String to,
            @Nullable String replyTo, String subject)
            throws MessagingException, UnsupportedEncodingException
    {
        Session session = Session.getInstance(System.getProperties());
        MimeMessage msg = new MimeMessage(session);

        msg.setFrom(new InternetAddress(from, fromName));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        if (Cfg.staging()) {
            msg.setSubject("[STAGING] " + subject, CHARSET);
        } else {
            msg.setSubject(subject, CHARSET);
        }

        if (replyTo != null) msg.setReplyTo(InternetAddress.parse(replyTo, false));

        return msg;
    }

    private static MimeMultipart createMultipartEmail(String textBody, @Nullable String htmlBody) throws MessagingException
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
     * emails are sent using the executor service which will always send one email at a time
     * This method is non blocking
     * @param msg
     * @throws MessagingException
     */
    private static Future<Void> sendEmail(final Message msg, final boolean usingSendGrid)
    {
        Future<Void> f = executor.submit(new Callable<Void>() {
            @Override
            public Void call() throws MessagingException
            {

                try {
                    Transport t = session.getTransport("smtp");
                    // we use SendGrid for any publically facing emails,
                    // and our own mail servers for internal emails.
                    try {
                        if (usingSendGrid) {
                            t.connect(SENDGRID_HOST,SENDGRID_USERNAME, SENDGRID_PASSWORD);
                        } else {
                            t.connect(LOCAL_HOST,LOCAL_USERNAME,LOCAL_PASSWORD);
                        }

                        t.sendMessage(msg, msg.getAllRecipients());
                    } finally {
                        t.close();
                    }
                    return null;
                } catch (MessagingException e) {
                    try {
                        String to = msg.getRecipients(Message.RecipientType.TO)[0].toString();
                        sendEmail(SVParam.SV_NOTIFICATION_SENDER, SVParam.SV_NOTIFICATION_SENDER,
                                SVParam.SV_NOTIFICATION_RECEIVER, null, "Messaging failed",
                                "Can't email " + to + ": " + e, null, false, null);

                        l.error("cannot send message to " + to + ": ", e);

                    } catch (Exception e1) {
                        l.error("cannot report email exception: ", e1);
                    }

                    throw e;
                }
            }
        });

        return f;
    }
}
