/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.sp.server.email.SmtpVerificationEmailer;
import com.mysql.jdbc.Util;
import org.slf4j.Logger;

import javax.mail.MessagingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * This class is used by the site configuration UI to send SMTP verifications emails to users during
 * setup.
 */
public class SmtpVerificationServlet extends HttpServlet
{
    private static final Logger l = Loggers.getLogger(SmtpVerificationServlet.class);
    private static final long serialVersionUID = 1L;

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
    }

    @Override
    protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
            throws IOException
    {
        // N.B. these params are defined in python land in setup.py. They must match.

        String fromEmail = req.getParameter("from_email");
        String toEmail = req.getParameter("to_email");
        String code = req.getParameter("verification_code");
        String host = req.getParameter("email_sender_public_host");
        String port = req.getParameter("email_sender_public_port");
        String username = req.getParameter("email_sender_public_username");
        String password = req.getParameter("email_sender_public_password");
        String _enable_tls = req.getParameter("email_sender_public_enable_tls").toLowerCase();
        String cert = req.getParameter("email_sender_public_cert");

        if (!_enable_tls.equals("true") && !_enable_tls.equals("false")) {
            String _error = "got invalid parameter email_sender_public_enable_tls = "  + _enable_tls;
            l.error(_error);
            resp.sendError(400, _error);
            return;
        }
        boolean enable_tls = _enable_tls.equals("true");

        try {
            l.debug("Sending SMTP verification email.\n" +
                    "from_email: " + fromEmail + '\n' +
                    "to_email: " + toEmail + "\n" +
                    "code: " + code + "\n" +
                    "smtp_host: " + host + "\n" +
                    "smtp_port: " + port + "\n" +
                    "smtp_username: " + username + "\n" +
                    "smtp_password length: " + password.length() + "\n" +
                    "smtp_enable_tls: " + enable_tls);

            SmtpVerificationEmailer.sendSmtpVerificationEmail(fromEmail, toEmail, code, host, port,
                    username, password, enable_tls, cert);
        } catch (MessagingException jmex) {
            l.error("Error sending mail", jmex);
            // the frontend will display the message of any exception that accompanies a 400 status
            resp.setStatus(400);
            resp.getWriter().print(jmex.getMessage());
        } catch (Exception e) {
            l.error("Unable to send email: " + Util.stackTraceToString(e));
            // the frontend will display the message of any exception that accompanies a 400 status
            resp.sendError(400, "unable to send email");
        }
    }

    /**
     * This servlet does not support GET.
     *
     * Return 200 anyway for the purposes of sanity checking.
     * */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException
    {
        resp.setStatus(200);
    }
}
