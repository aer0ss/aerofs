/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import com.aerofs.sp.server.IRequestProvider;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class RequestRemoteAddress
{
    private final IRequestProvider _request;

    public RequestRemoteAddress(IRequestProvider request)
    {
        _request = request;
    }

    public String get()
    {
        return _request.get().getHeader("X-Real-IP");
    }
}
