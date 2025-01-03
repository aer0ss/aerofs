/*
 * Copyright (c) Air Computing Inc., 2013.
 */
package com.aerofs.restless.netty;

import com.aerofs.restless.Configuration;
import com.aerofs.restless.Service;
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
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

class JerseyResponseWriter implements ContainerResponseWriter
{
    private final static Logger l = LoggerFactory.getLogger(JerseyResponseWriter.class);

    private final Configuration _config;

    private final Channel _channel;
    private final String _location;
    private final boolean _keepAlive;
    private final HttpRequest _request;

    private ChannelBuffer _buffer;

    private static final HttpChunk EMPTY = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);

    public JerseyResponseWriter(final Channel channel, HttpRequest request,
            Configuration config)
    {
        _channel = channel;
        _request = request;
        _config = config;

        _location = "https://" + request.headers().get(Names.HOST) + "/";
        _keepAlive = HttpHeaders.isKeepAlive(request);
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
            r.headers().set(header.getKey(), values);
            values.clear();
        }

        _config.addGlobalHeaders(_request, r);

        Object entity = response.getEntity();
        // entity-less responses should have a truly empty body which means
        // no chunked transfer encoding (otherwise we get an empty trailing
        // chunk which confuses many HTTP parsers)
        if (entity == null) r.headers().set(Names.CONTENT_LENGTH, "0");

        if (r.headers().get(Names.CONTENT_LENGTH) == null) {
            // if no explicit Content-Length is set, use chunked transfer-encoding
            // -> can stream content of unknown length on a persistent connection
            r.setChunked(true);
            r.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
        }

        // rewrite Location: header if needed
        String location = r.headers().get(Names.LOCATION);
        if (location != null && location.startsWith(Service.DUMMY_LOCATION)) {
            r.headers().set(Names.LOCATION, location.replace(Service.DUMMY_LOCATION, _location));
        }

        // write response status and headers
        l.debug("response: {}", r);
        _channel.write(r);

        if (entity instanceof ContentStream) {
            // Jersey does not work well with async streaming, we need to take over...
            // NB: because we bypass jersey, we need to manually inhibit body writing for HEAD
            if (!_request.getMethod().equals(HttpMethod.HEAD)) {
                _channel.write(new ChunkedContentStream((ContentStream)entity, _channel.getId()))
                        .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
            ChannelFuture cf = _channel.write(EMPTY);
            if (!_keepAlive) cf.addListener(ChannelFutureListener.CLOSE);

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
        if (_buffer != null) {
            _channel.write(new DefaultHttpChunk(_buffer))
                    .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            ChannelFuture cf = _channel.write(EMPTY);
            if (!_keepAlive) cf.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Nullable String partialResponse() {
        return _buffer != null ? _buffer.toString(StandardCharsets.UTF_8) : null;
    }
}
