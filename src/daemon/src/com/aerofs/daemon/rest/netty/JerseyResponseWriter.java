/*
 * Copyright (c) Air Computing Inc., 2013.
 */
package com.aerofs.daemon.rest.netty;

import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseWriter;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class JerseyResponseWriter implements ContainerResponseWriter
{
    private final Channel _channel;
    private HttpResponse _response;

    public JerseyResponseWriter(final Channel channel)
    {
        _channel = channel;
    }

    @Override
    public OutputStream writeStatusAndHeaders(final long contentLength, final ContainerResponse response)
            throws IOException
    {
        _response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.getStatus()));
        final List<String> values = new ArrayList<String>();
        for (Map.Entry<String, List<Object>> header : response.getHttpHeaders().entrySet()) {
            for (Object value : header.getValue()) {
                values.add(ContainerResponse.getHeaderValue(value));
            }
            _response.setHeader(header.getKey(), values);
            values.clear();
        }

        final ChannelBuffer buffer = ChannelBuffers.dynamicBuffer();
        _response.setContent(buffer);
        return new ChannelBufferOutputStream(buffer);
    }

    @Override
    public void finish() throws IOException
    {
        _channel.write(_response).addListener(ChannelFutureListener.CLOSE);
    }
}
