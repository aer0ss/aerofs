/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.sp.server.lib.session;

import javax.servlet.http.HttpSession;

/**
 * Interface that provides access to an HTTP session object.
 */
public interface IHttpSessionProvider
{
    public HttpSession get();
}
