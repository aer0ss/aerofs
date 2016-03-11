package com.aerofs.polaris.external_api;

import com.google.common.base.Joiner;
import org.jboss.netty.handler.codec.http.HttpHeaders;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;

public class CORSFilter implements ContainerResponseFilter
{
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException
    {
        MultivaluedMap<String, Object> respHeaders = responseContext.getHeaders();
        respHeaders.add(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        respHeaders.add(HttpHeaders.Names.ACCESS_CONTROL_EXPOSE_HEADERS,
                Joiner.on(",").join(respHeaders.keySet()));
    }
}
