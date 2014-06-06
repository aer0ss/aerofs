/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import javax.servlet.http.HttpServletRequest;

public interface IRequestProvider
{
    HttpServletRequest get();
}
