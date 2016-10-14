/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import javax.servlet.http.HttpServletRequest;

public class ThreadLocalRequestProvider implements IRequestProvider
{
    private static final ThreadLocal<HttpServletRequest> _request =
            new ThreadLocal<HttpServletRequest>();

    public void set(HttpServletRequest request)
    {
        _request.set(request);
    }

    @Override
    public HttpServletRequest get()
    {
        return _request.get();
    }
}
