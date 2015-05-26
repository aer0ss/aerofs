/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import com.aerofs.base.Loggers;
import com.aerofs.sp.server.IRequestProvider;
import org.slf4j.Logger;

public class RequestRemoteAddress
{
    private final Logger l = Loggers.getLogger(RequestRemoteAddress.class);
    private final IRequestProvider _request;

    public RequestRemoteAddress(IRequestProvider request)
    {
        _request = request;
    }

    public String get()
    {
        String ip = _request.get().getHeader("X-Real-IP");
        if (ip == null) {
            l.warn("no X-Real-IP header. Use remote address");
            ip = _request.get().getRemoteAddr();
        }
        return ip;
    }
}
