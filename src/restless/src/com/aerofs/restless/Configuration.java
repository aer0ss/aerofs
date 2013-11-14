/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.restless;

import com.aerofs.base.Version;
import org.jboss.netty.handler.codec.http.HttpResponse;

import javax.ws.rs.core.Response;

public interface Configuration
{
    // global headers (cache control, Cross-Origin Resource Sharing, ...)
    void addGlobalHeaders(HttpResponse response);

    Response resourceNotFound(String path);

    boolean isSupportedVersion(Version version);
}
