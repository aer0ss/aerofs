package com.aerofs.servlets;

import com.aerofs.l.ILabeling;
import com.aerofs.l.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Util;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

public class AeroServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;

    protected void init_() throws ServletException
    {
        try {
            // setup App Root
            String appRoot = Util.join(getServletContext().getRealPath("/"), "WEB-INF");
            AppRoot.set(appRoot);

            // setup labeling
            String cls = getServletContext().getInitParameter("labeling_class");
            L.set((ILabeling) Class.forName(cls).newInstance());
        } catch (Exception e) {
            Util.l(this).error("init: ", e);
            throw new ServletException(e);
        }
    }
}
