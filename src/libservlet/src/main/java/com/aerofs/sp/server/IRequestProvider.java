/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import javax.servlet.http.HttpServletRequest;

/**
 * A class that wraps the request context so I can have a static threadlocal provide access
 * to the context rather than having to pass it down through the entire call chain.
 *
 * Since in the servlet world, it's thread-per-request handlers anyway, this makes for more
 * readable code.  Also, the protobuf RPC generator doesn't pass sufficient context to the service
 * reactor, so this is a convenient way to get the context to where it'll be used.
 */
public interface IRequestProvider
{
    HttpServletRequest get();
}
