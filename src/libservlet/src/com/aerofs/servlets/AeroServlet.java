package com.aerofs.servlets;

import com.aerofs.base.Loggers;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Util;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

public class AeroServlet extends HttpServlet
{
    private static final Logger l = Loggers.getLogger(AeroServlet.class);

    private static final long serialVersionUID = 1L;

    protected void init_() throws ServletException
    {
        try {
            // setup App Root
            // TODO (WW) is it still needed after removal of dynamic labeling?
            String appRoot = Util.join(getServletContext().getRealPath("/"), "WEB-INF");
            AppRoot.set(appRoot);
        } catch (Exception e) {
            l.error("init: ", e);
            throw new ServletException(e);
        }
    }
}
