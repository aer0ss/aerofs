/*
 * Copyright (c) Air Computing Inc., 2013.
 */
package com.aerofs.restless.netty;

import com.aerofs.restless.Configuration;
import com.aerofs.restless.stream.ContentStream;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseWriter;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

class JerseyResponseWriter implements ContainerResponseWriter
{
    private final Configuration _config;

    private final Channel _channel;
    private final boolean _keepAlive;

    private ChannelBuffer _buffer;
    private final ChannelFutureListener _trailer;

    private static final HttpChunk EMPTY = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);

    public JerseyResponseWriter(final Channel channel, boolean keepAlive, Configuration config)
    {
        _config = config;

        _channel = channel;
        _keepAlive = keepAlive;
        _trailer = new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture cf)
            {
                if (cf.isSuccess()) {
                    cf = cf.getChannel().write(EMPTY);
                    if (!_keepAlive) cf.addListener(CLOSE);
                } else {
                    cf.getChannel().close();
                }
            }
        };
    }

    @Override
    public OutputStream writeStatusAndHeaders(long contentLength, ContainerResponse response)
            throws IOException
    {
        HttpResponse r = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(response.getStatus()));

        final List<String> values = Lists.newArrayList();
        for (Map.Entry<String, List<Object>> header : response.getHttpHeaders().entrySet()) {
            for (Object value : header.getValue()) {
                values.add(ContainerResponse.getHeaderValue(value));
            }
            r.setHeader(header.getKey(), values);
            values.clear();
        }

        _config.addGlobalHeaders(r);

        if ((r.getHeader(Names.CONTENT_LENGTH) == null)) {
            // if no explicit Content-Length is set, use chunked transfer-encoding
            // -> can stream content of unknown length on a persistent connection
            r.setChunked(true);
            r.setHeader(Names.TRANSFER_ENCODING, Values.CHUNKED);
        }

        // write response status and headers
        _channel.write(r);

        Object entity = response.getEntity();
        if (entity instanceof ContentStream) {
            // Jersey does not work well with async streaming, we need to take over...
            _channel.write(new ChunkedContentStream((ContentStream)entity)).addListener(_trailer);

            // ContentStreamProvider should not try to write to the stream but just in case...
            return ByteStreams.nullOutputStream();
        } else {
            _buffer = ChannelBuffers.dynamicBuffer();
            return new ChannelBufferOutputStream(_buffer);
        }
    }

    @Override
    public void finish() throws IOException
    {
        if (_buffer != null) _channel.write(new DefaultHttpChunk(_buffer)).addListener(_trailer);
    }
}
