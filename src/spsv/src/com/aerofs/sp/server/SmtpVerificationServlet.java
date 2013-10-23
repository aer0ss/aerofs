/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.sp.server.email.SmtpVerificationEmailer;
import com.mysql.jdbc.Util;
import org.slf4j.Logger;

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

        String toEmail = req.getParameter("to_email");
        String code = req.getParameter("code");
        String host = req.getParameter("smtp_host");
        String port = req.getParameter("smtp_port");
        String username = req.getParameter("smtp_username");
        String password = req.getParameter("smtp_password");

        try {
            l.debug("Sending SMTP verification email.\n" +
                    "to_email: " + toEmail + "\n" +
                    "code: " + code + "\n" +
                    "smtp_host: " + host + "\n" +
                    "smtp_port: " + port + "\n" +
                    "smtp_username: " + username + "\n" +
                    "smtp_password: " + password);

            SmtpVerificationEmailer.sendSmtpVerificationEmail(toEmail, code, host, port, username,
                    password);
        }
        catch (Exception e) {
            l.error("Unable to send email: " + Util.stackTraceToString(e));
            resp.sendError(400, "unable to send email");
        }
    }

    /** This servlet does not support GET. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException
    {
        resp.sendError(405);
    }
}
