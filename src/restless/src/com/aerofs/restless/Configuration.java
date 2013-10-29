/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.restless;

import org.jboss.netty.handler.codec.http.HttpResponse;

import javax.ws.rs.core.Response;
import java.net.URI;

/**
 *
 */
public interface Configuration
{
    // global headers (cache control, Cross-Origin Resource Sharing, ...)
    void addGlobalHeaders(HttpResponse response);

    Response URINotFound(URI uri);
}
