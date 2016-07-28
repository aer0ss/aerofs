/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.http;

import com.aerofs.baseline.metrics.MetricRegistries;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.Timeout;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.ContainerException;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spi.Container;
import org.glassfish.jersey.server.spi.ContainerResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@NotThreadSafe
final class HttpRequestHandler extends ChannelInboundHandlerAdapter implements Container {

    private static final String NETTY_HTTP_DECODING_FAILED_URI = "/bad-request";
    private static final long ZERO_CONTENT_LENGTH = 0;

    private static final SecurityContext DEFAULT_SECURITY_CONTEXT = new SecurityContext() {
        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public String getAuthenticationScheme() {
            return null;
        }
    };

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestHandler.class);

    // response status metrics
    private static final Meter STATUS_1XX_METER = MetricRegistries.getRegistry().meter(MetricRegistries.name("http", "response", "1xx"));
    private static final Meter STATUS_2XX_METER = MetricRegistries.getRegistry().meter(MetricRegistries.name("http", "response", "2xx"));
    private static final Meter STATUS_3XX_METER = MetricRegistries.getRegistry().meter(MetricRegistries.name("http", "response", "3xx"));
    private static final Meter STATUS_4XX_METER = MetricRegistries.getRegistry().meter(MetricRegistries.name("http", "response", "4xx"));
    private static final Meter STATUS_5XX_METER = MetricRegistries.getRegistry().meter(MetricRegistries.name("http", "response", "5xx"));

    // other metrics
    private static final Timer REQUEST_TIMER = MetricRegistries.getRegistry().timer(MetricRegistries.name("http", "request", "service-time"));
    private static final Meter SUSPEND_METER = MetricRegistries.getRegistry().meter(MetricRegistries.name("http", "request", "suspend"));
    private static final Meter SUCCESS_METER = MetricRegistries.getRegistry().meter(MetricRegistries.name("http", "request", "success"));
    private static final Meter FAILURE_METER = MetricRegistries.getRegistry().meter(MetricRegistries.name("http", "request", "failure"));
    private static final Histogram CONTENT_LENGTH_HISTOGRAM = MetricRegistries.getRegistry().histogram(MetricRegistries.name("http", "response", "content-length"));

    private static final MapPropertiesDelegate PROPERTIES_DELEGATE = new MapPropertiesDelegate();

    private final URI baseUri;
    private final io.netty.util.Timer timer;
    private final Executor applicationExecutor;
    private final ApplicationHandler applicationHandler;

    private Runnable savedRequestRunnable;
    private volatile PendingRequest pendingRequest; // may be accessed from within a request-processing thread

    HttpRequestHandler(ApplicationHandler applicationHandler, URI baseUri, Executor applicationExecutor, io.netty.util.Timer timer) {
        this.applicationHandler = applicationHandler;
        this.baseUri = baseUri;
        this.applicationExecutor = applicationExecutor;
        this.timer = timer;
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        LOGGER.trace("{}: set http cleanup handler", Channels.getHexText(ctx));
        ctx.channel().closeFuture().addListener(future -> cleanup(ctx, future.cause()));
        super.channelRegistered(ctx);
        ctx.read();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cleanup(ctx, cause);
        ctx.fireExceptionCaught(cause);
    }

    private void cleanup(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.trace("{}: run http cleanup handler", Channels.getHexText(ctx), cause);

        // we're still waiting for the request to be processed
        // so, destroy the input and output streams associated
        // with the current request
        if (pendingRequest != null) {
            pendingRequest.closeStreams();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (ctx.channel().closeFuture().isDone()) {
            LOGGER.warn("{}: drop http message - channel closed", Channels.getHexText(ctx));
            return;
        }

        if (!(msg instanceof HttpObject)) {
            super.channelRead(ctx, msg);
        }

        //
        // netty http decoding:
        //
        // 1. normal case: HttpRequest (headers), HttpContent (0+), LastHttpContent (trailing headers)
        //    NOTE: with chunked transfer encoding or content-length non-zero you get an arbitrary number of HttpContent objects
        // 2. error case (failed to decode an HTTP message): HttpRequest with NETTY_HTTP_DECODING_FAILED_URI
        //

        if (msg instanceof HttpRequest) {
            // this is the first object netty generates:
            // an HttpRequest containing a number of headers

            // we should not have another request pending
            Preconditions.checkState(pendingRequest == null, "previous request pending:%s", pendingRequest);

            // cast it
            HttpRequest nettyRequest = (HttpRequest) msg;

            // get the request id
            String requestId = nettyRequest.headers().get(Headers.REQUEST_TRACING_HEADER);
            Preconditions.checkState(requestId != null, "http request on %s has no request id", Channels.getHexText(ctx));

            // check if http decoding failed and if so, abort early
            if (nettyRequest.uri().equals(NETTY_HTTP_DECODING_FAILED_URI)) {
                LOGGER.warn("{}: [{}] fail http decoding", Channels.getHexText(ctx), requestId);
                ctx.read();
                return;
            }

            // get a few headers we really care about
            HttpVersion httpVersion = nettyRequest.protocolVersion();
            boolean keepAlive = HttpHeaders.isKeepAlive(nettyRequest);
            boolean transferEncodingChunked = HttpHeaders.isTransferEncodingChunked(nettyRequest);
            boolean continueExpected = HttpHeaders.is100ContinueExpected(nettyRequest);
            long contentLength = HttpHeaders.getContentLength(nettyRequest, ZERO_CONTENT_LENGTH);
            boolean hasContent = transferEncodingChunked || contentLength > ZERO_CONTENT_LENGTH;
            LOGGER.trace("{}: [{}] rq:{} ka:{} ck:{} ce:{} cl:{}", Channels.getHexText(ctx), requestId, nettyRequest, keepAlive, transferEncodingChunked, continueExpected, contentLength);

            // create the input stream used to read content
            ContentInputStream entityInputStream;
            if (hasContent) {
                entityInputStream = new EntityInputStream(httpVersion, continueExpected, ctx);
            } else {
                entityInputStream = EmptyEntityInputStream.EMPTY_ENTITY_INPUT_STREAM;
            }

            // create the object with which to write the response body
            PendingRequest pendingRequest = new PendingRequest(requestId, httpVersion, keepAlive, entityInputStream, ctx);

            // create the jersey request object
            final ContainerRequest jerseyRequest = new ContainerRequest(baseUri, URI.create(nettyRequest.uri()), nettyRequest.method().name(), DEFAULT_SECURITY_CONTEXT, PROPERTIES_DELEGATE);
            jerseyRequest.setProperty(RequestProperties.REQUEST_CONTEXT_CHANNEL_ID_PROPERTY, new ChannelId(Channels.getHexText(ctx)));
            jerseyRequest.setProperty(RequestProperties.REQUEST_CONTEXT_REQUEST_ID_PROPERTY, new RequestId(requestId));
            jerseyRequest.header(Headers.REQUEST_TRACING_HEADER, requestId); // add request id to headers
            copyHeaders(nettyRequest.headers(), jerseyRequest); // copy headers from message
            jerseyRequest.setEntityStream(entityInputStream);
            jerseyRequest.setWriter(pendingRequest);

            // now we've got all the initial headers and are waiting for the entity
            this.pendingRequest = pendingRequest;

            // store the runnable that we want jersey to execute
            saveRequestRunnable(() -> {
                // all throwables caught by jersey internally -
                // handled by the ResponseWriter below
                // if, for some reason there's some weird error it'll be handled
                // by the default exception handler, which kills the process
                applicationHandler.handle(jerseyRequest);
            });

            // IMPORTANT:
            // we pass this request up to be processed by
            // jersey before we've read any content. This allows
            // the resource to read from the InputStream
            // directly, OR, to use an @Consumes annotation with an
            // input objectType to invoke the appropriate parser
            //
            // since the request is consumed *before* the content
            // has been received readers may block. to prevent the
            // IO thread from blocking we have to execute all
            // request processing in an application threadpool
            if (hasContent) {
                submitPendingRunnable();
            }

            // indicate that we want to keep reading
            // this is always the case when we receive headers
            // because we want to receive everything until
            // LastHttpContent
            ctx.read();
        } else {
            // after receiving the http headers we get
            // a series of HttpContent objects that represent
            // the entity or a set of chunks

            // we should have received the headers already
            Preconditions.checkState(pendingRequest != null, "no pending request");
            // we're not expecting anything other than content objects right now
            Preconditions.checkArgument(msg instanceof HttpContent, "HttpContent expected, not %s", msg.getClass().getSimpleName());

            // handle the content
            HttpContent content = (HttpContent) msg;
            boolean last = msg instanceof LastHttpContent;
            LOGGER.trace("{}: [{}] handling content:{} last:{}", Channels.getHexText(ctx), pendingRequest.requestId, content.content().readableBytes(), last);
            pendingRequest.entityInputStream.addBuffer(content.content(), last); // transfers ownership to the HttpContentInputStream

            // FIXME (AG): support trailing headers
            // if it's the last piece of content, then we're done
            if (last) {
                // submit the request to jersey if we haven't yet
                if (savedRequestRunnable != null) {
                    submitPendingRunnable();
                }
            }
        }
    }

    private void submitPendingRunnable() {
        Preconditions.checkState(this.savedRequestRunnable != null, "no pending request runnable");

        // get the saved request runnable
        Runnable requestRunnable = savedRequestRunnable;
        savedRequestRunnable = null;

        // this may throw if the execution is rejected,
        // in which case the channel should be automatically closed
        // by a later handler in the chain
        applicationExecutor.execute(requestRunnable);
    }

    private void saveRequestRunnable(Runnable requestRunnable) {
        Preconditions.checkState(this.savedRequestRunnable == null, "pending request runnable exists");
        savedRequestRunnable = requestRunnable;
    }

    private void copyHeaders(HttpHeaders headers, ContainerRequest destination) {
        for (Map.Entry<String, String> header : headers) {
            destination.header(header.getKey(), header.getValue());
        }
    }

    @Override
    public ApplicationHandler getApplicationHandler() {
        return applicationHandler;
    }

    @Override
    public ResourceConfig getConfiguration() {
        throw new UnsupportedOperationException("no resource configuration used");
    }

    @Override
    public void reload() {
        throw new UnsupportedOperationException("reloading unsupported");
    }

    @Override
    public void reload(ResourceConfig configuration) {
        throw new UnsupportedOperationException("reloading unsupported");
    }

    private final class PendingRequest implements ContainerResponseWriter {

        private final Timer.Context timerContext = REQUEST_TIMER.time();
        private final String requestId;
        private final HttpVersion httpVersion;
        private final boolean keepAlive;
        private final ContentInputStream entityInputStream;
        private final ChannelHandlerContext ctx;

        private volatile TimeoutHandler timeoutHandler;
        private volatile Timeout timeoutReference;

        // FIXME (AG): does this have to be volatile? I think so, so deal with exceptions thrown in the netty pipeline
        private volatile ContentOutputStream entityOutputStream;

        public PendingRequest(String requestId, HttpVersion httpVersion, boolean keepAlive, ContentInputStream entityInputStream, ChannelHandlerContext ctx) {
            this.requestId = requestId;
            this.httpVersion = httpVersion;
            this.keepAlive = keepAlive;
            this.entityInputStream = entityInputStream;
            this.ctx = ctx;
        }

        // if this returns false then jersey never buffers
        // this means that contentLength in writeResponseStatusAndHeaders is always -1
        @Override
        public boolean enableResponseBuffering() {
            return true;
        }

        @Override
        public OutputStream writeResponseStatusAndHeaders(long contentLength, ContainerResponse jerseyResponse) throws ContainerException {
            int status = jerseyResponse.getStatus();

            LOGGER.debug("{}: [{}] write status and headers st:{} cl:{}", Channels.getHexText(ctx), requestId, status, contentLength);
            meterStatus(status);

            // create the netty response
            HttpResponse nettyResponse = new DefaultHttpResponse(httpVersion, HttpResponseStatus.valueOf(status));
            copyHeaders(jerseyResponse, nettyResponse);

            // add the request id to the header
            nettyResponse.headers().add(Headers.REQUEST_TRACING_HEADER, requestId);

            // add a Connection: Close header if required
            if (!keepAlive) {
                nettyResponse.headers().add(Names.CONNECTION, Values.CLOSE);
            }

            // create the content buffer if necessary
            if (contentLength < 0) {
                LOGGER.trace("{}: [{}] chunked", Channels.getHexText(ctx), requestId);
                nettyResponse.headers().add(Names.TRANSFER_ENCODING, Values.CHUNKED);
                ctx.writeAndFlush(nettyResponse);
                entityOutputStream = new EntityOutputStream(ctx, CONTENT_LENGTH_HISTOGRAM);
            } else if (contentLength == 0) {
                LOGGER.trace("{}: [{}] no content", Channels.getHexText(ctx), requestId);
                nettyResponse.headers().add(Names.CONTENT_LENGTH, 0);
                ctx.write(nettyResponse);
                entityOutputStream = new EmptyEntityOutputStream(ctx);
            } else {
                LOGGER.trace("{}: [{}] non-empty body", Channels.getHexText(ctx), requestId);
                nettyResponse.headers().add(Names.CONTENT_LENGTH, contentLength);
                ctx.write(nettyResponse); // don't flush now - only do so when all the content is written
                entityOutputStream = new EntityOutputStream(ctx, CONTENT_LENGTH_HISTOGRAM);
            }

            return entityOutputStream;
        }

        private void meterStatus(int status) {
            if (status <= 100) {
                STATUS_1XX_METER.mark();
            } else if (status <= 300) {
                STATUS_2XX_METER.mark();
            } else if (status <= 400) {
                STATUS_3XX_METER.mark();
            } else if (status <= 500) {
                STATUS_4XX_METER.mark();
            } else {
                STATUS_5XX_METER.mark();
            }
        }

        private void copyHeaders(ContainerResponse sourceResponse, HttpResponse destinationResponse) {
            for (Map.Entry<String, List<Object>> header : sourceResponse.getHeaders().entrySet()) {
                List<String> values = Lists.newArrayList();
                values.addAll(header.getValue().stream().map(Object::toString).collect(Collectors.toList()));
                destinationResponse.headers().add(header.getKey(), values);
            }
        }

        // IMPORTANT: jersey will guarantee that suspend is called
        // *before* the method is called (see ResourceMethodInvoker)
        // this guarantees that if the user calls setSuspendTimeout from
        // within the request thread timeoutHandler will have a valid value
        // that said, setSuspendTimeout may be called from any user
        // thread, so I avoid visibility issues by making timeoutHandler volatile

        @Override
        public boolean suspend(long timeout, TimeUnit timeUnit, final TimeoutHandler timeoutHandler) {
            Preconditions.checkState(this.timeoutHandler == null, "timeout handler %s exists", this.timeoutHandler);
            Preconditions.checkArgument(timeout == AsyncResponse.NO_TIMEOUT, "non-zero timeout %s specified in suspend", timeout);

            LOGGER.trace("{}: [{}] suspend indefinitely", Channels.getHexText(ctx), requestId);
            SUSPEND_METER.mark();

            this.timeoutHandler = timeoutHandler;
            return true;
        }

        // IMPORTANT: jersey ensures that onTimeout and
        // commit/failure don't run at the same time

        @Override
        public void setSuspendTimeout(long timeout, TimeUnit timeUnit) throws IllegalStateException {
            LOGGER.debug("{}: [{}] set suspend timeout to:{} tu:{}", Channels.getHexText(ctx), requestId, timeout, timeUnit);

            // jersey appears to swallow ISEs. Amazing.
            Preconditions.checkState(this.timeoutHandler != null, "no timeout handler");

            // always allow the timeout object to be set
            // this means that the caller can keep pushing the timeout forward
            // everything but the last timeout is ignored
            timeoutReference = timer.newTimeout(timeoutHandle -> {
                if (timeoutHandle == timeoutReference) {
                    PendingRequest.this.timeoutHandler.onTimeout(PendingRequest.this);
                } else {
                    LOGGER.debug("{}: [{}] ignore timeout", Channels.getHexText(ctx), requestId);
                }
            }, timeout, timeUnit);
        }

        @Override
        public void commit() {
            LOGGER.trace("{}: [{}] done process request", Channels.getHexText(ctx), requestId);

            closeStreams();

            if (!keepAlive) {
                Channels.expectedClose(ctx, "not keep-alive connection");
            }

            // only after we've done the cleanup do we want to update the metrics
            SUCCESS_METER.mark();

            // finally, indicate that we're ready to read again
            ctx.read();
        }

        @Override
        public void failure(Throwable error) {
            LOGGER.warn("{}: id:{} fail process request", Channels.getHexText(ctx), requestId, error);

            // close the channel first
            // then, when we close streams they'll attempt to dump
            // data over the netty pipeline, which will result in the
            // http objects being rejected and reference count
            // properly decremented. Yeah. So hacky. So not sexy.
            Channels.close(ctx, "request failure");

            if (entityOutputStream != null) {
                entityOutputStream.markError();
            }

            closeStreams();

            // only after we've done the cleanup do we want to update the metrics
            FAILURE_METER.mark();
        }

        // NOTE: called both in normal conditions and error conditions
        private void closeStreams() {
            LOGGER.debug("{}: [{}] close http req/rsp entity streams", Channels.getHexText(ctx), requestId);

            // since I'm guaranteed that this method is called
            // no matter what, I'll turn off the timers here
            timerContext.stop();

            try {
                Closeables.close(entityInputStream, true);
            } catch (IOException e) {
                LOGGER.warn("{}: [{}] fail close entity istream", Channels.getHexText(ctx), requestId, e);
            }

            try {
                Closeables.close(entityOutputStream, true);
            } catch (IOException e) {
                LOGGER.warn("{}: [{}] fail close entity ostream", Channels.getHexText(ctx), requestId, e);
            }

            // we've finished processing this request
            // reset and prepare for the next request
            pendingRequest = null;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("channelId", Channels.getHexText(ctx))
                    .add("requestId", requestId)
                    .add("keepAlive", keepAlive)
                    .toString();
        }
    }
}
