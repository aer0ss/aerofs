/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * This class is used by the site configuration UI to verify LDAP settings during setup.
 */
public class LdapVerificationServlet extends HttpServlet
{
    private static final Logger l = Loggers.getLogger(LdapVerificationServlet.class);
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
        // TODO (MP) tests.
    }

    /** This servlet does not support GET. */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException
    {
        resp.sendError(405);
    }
}
