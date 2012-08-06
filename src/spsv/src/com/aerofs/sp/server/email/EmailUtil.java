package com.aerofs.sp.server.email;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.aerofs.lib.cfg.Cfg;
import org.apache.log4j.Logger;


import static com.aerofs.sp.server.SPSVParam.*;

public class EmailUtil
{
    private static final Logger l = com.aerofs.lib.Util.l(EmailUtil.class);

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    protected static final String SENDGRID_HOST = "smtp.sendgrid.net";
    protected static final String SENDGRID_USERNAME = "yuri@aerofs.com";
    protected static final String SENDGRID_PASSWORD = "2vyXBb2sJDWl";
    private static String LOCAL_HOST = "svmail.aerofs.com";
    private static String LOCAL_USERNAME = "noreply";
    private static String LOCAL_PASSWORD = "qEphE2uzuBr5";
    private static Session session = null;

    static final String CHARSET = "UTF-8";

    static {
        Properties props = System.getProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable","true");
        props.put("mail.smtp.startls.required", "true");

        session = Session.getInstance(System.getProperties());
    }

    /*
     * @param replyTo may be null
     * @param body may be null
     */
    public static MimeMessage composeEmail(String from, String fromName, String to,
            @Nullable String replyTo, String subject, String body)
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
        if (body != null) msg.setText(body, CHARSET);
        if (replyTo != null) msg.setReplyTo(InternetAddress.parse(replyTo, false));

        return msg;
    }

    /**
     * emails are sent using the executor service which will always send one email at a time
     * This method is non blocking
     * @param msg
     * @throws MessagingException
     */
    public static void sendEmail(final Message msg, final boolean usingSendGrid)
    {
        executor.execute(new Runnable() {
            @Override
            public void run()
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
                } catch (MessagingException e) {
                    try {
                        String to = msg.getRecipients(Message.RecipientType.TO)[0].toString();
                        emailSVNotification("Messaging failed", "Can't email " + to + ": " + e);

                        l.error("cannot send message to " + to + ": ", e);
                    } catch (Exception e1) {
                        l.error("cannot report email exception: ", e);
                    }
                }
            }
        });
    }

    public static void emailSVNotification(final String subject, final String body)
    {
        try {
            EmailUtil.sendEmail(composeEmail(SV_NOTIFICATION_SENDER, SV_NOTIFICATION_SENDER,
                    SV_NOTIFICATION_RECEIVER, null, subject, body), false);
        } catch (Exception e) {
            l.error("cannot email notification: ", e);
        }
    }

    public static void emailSPNotification(final String subject, final String body)
    {
        try {
            EmailUtil.sendEmail(composeEmail(SP_NOTIFICATION_SENDER, SP_NOTIFICATION_SENDER,
                    SP_NOTIFICATION_RECEIVER, null, subject, body), false);
        } catch (Exception e) {
            l.error("cannot email notification: ", e);
        }
    }

}
