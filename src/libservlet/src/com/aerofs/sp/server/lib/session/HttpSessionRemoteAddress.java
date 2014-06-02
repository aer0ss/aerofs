/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class HttpSessionRemoteAddress extends AbstractHttpSession
{
    private static final String SESS_ATTR_REMOTE_ADDRESS = "remote_address";

    public HttpSessionRemoteAddress(IHttpSessionProvider sessionProvider)
    {
        super(sessionProvider);
    }

    public void init(HttpServletRequest req)
            throws IOException
    {
        // N.B. our nginx config sets X-Real-IP to $remote_addr
        String remoteAddress = req.getHeader("X-Real-IP");
        getSession().setAttribute(SESS_ATTR_REMOTE_ADDRESS, remoteAddress);
    }

    public String get()
    {
        return (String) getSession().getAttribute(SESS_ATTR_REMOTE_ADDRESS);
    }
}
