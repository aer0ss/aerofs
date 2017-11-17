/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre.proxy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.aerofs.base.BaseUtil;
import com.aerofs.base.net.NettyUtil;
import com.aerofs.havre.RequestRouter;
import com.aerofs.ids.DID;
import com.aerofs.havre.Authenticator;
import com.aerofs.havre.Authenticator.UnauthorizedUserException;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.havre.Version;
import com.aerofs.ids.UniqueID;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.base.SimpleHttpClient;
import com.aerofs.testlib.AbstractBaseTest;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jayway.restassured.RestAssured;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestHttpProxyServer extends AbstractBaseTest
{
    @Mock Authenticator auth;
    @Mock EndpointConnector connector;
    @Mock RequestRouter router;

    HttpProxyServer proxy;
    Timer timer;

    static {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<>();
        console.setContext(context);
        console.setEncoder(newEncoder(context));
        console.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(console);
        context.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    private static PatternLayoutEncoder newEncoder(LoggerContext context)
    {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%-5level [%date{ISO8601, UTC}] [%-8.8thread] %c{0}: %m%n%xEx");
        encoder.setContext(context);
        encoder.setCharset(BaseUtil.CHARSET_UTF);
        encoder.start();
        return encoder;
    }

    @Before
    public void setUp()
    {
        // tweak timeout to prevent 504 test from taking ages
        HttpRequestProxyHandler.READ_TIMEOUT = 100;
        HttpRequestProxyHandler.WRITE_TIMEOUT = 300;
        HttpRequestProxyHandler.TIMEOUT_UNIT = TimeUnit.MILLISECONDS;

        when(router.route(anyString(), anyListOf(DID.class))).thenReturn(DID.generate());

        timer = new HashedWheelTimer();
        proxy = new HttpProxyServer(new InetSocketAddress(0), null, timer, auth, connector, router) {
            @Override
            protected ServerSocketChannelFactory getServerSocketFactory()
            {
                return new NioServerSocketChannelFactory(
                        Executors.newSingleThreadExecutor(), 1,
                        Executors.newSingleThreadExecutor(), 1);
            }
        };
        proxy.start();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = proxy.getListeningPort();
    }

    @After
    public void tearDown()
    {
        proxy.stop();
        timer.stop();
    }

    @Test
    public void shouldReturn401WhenAccessTokenMissing() throws Exception
    {
        when(auth.authenticate(anyString())).thenThrow(new UnauthorizedUserException());

        expect()
                .statusCode(401)
        .when()
                .get("/");
    }

    @Test
    public void shouldReturn503WhenNoDeviceAvailable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");
        when(auth.authenticate(anyString())).thenReturn(user);

        given()
                .header(Names.AUTHORIZATION, "Bearer foo")
        .expect()
                .statusCode(503)
        .when()
                .get("/");
    }

    @Test
    public void shouldReturn503WhenRouteNotAvailable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");
        DID did = DID.generate();

        when(auth.authenticate(anyString())).thenReturn(user);

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(mock(Channel.class));
        when(connector.connect(eq(user), eq(did), eq(true), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(null);

        given()
                .header(Names.AUTHORIZATION, "Bearer foo")
                .header("Route", did.toStringFormal())
        .expect()
                .statusCode(503)
        .when()
                .get("/");
    }

    @Test
    public void shouldReturn503WhenUploadTargetNotAvailable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");
        DID did = DID.generate();

        when(auth.authenticate(anyString())).thenReturn(user);

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(mock(Channel.class));
        when(connector.connect(eq(user), eq(did), eq(true), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(null);

        given()
                .header(Names.AUTHORIZATION, "Bearer foo")
                .header("Upload-ID", did.toStringFormal() + UniqueID.generate().toStringFormal())
        .expect()
                .statusCode(503)
        .when()
            .put("/");
    }

    @Test
    public void shouldReturn503WhenLegacyUploadTargetNotAvailable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");
        DID did = DID.generate();

        when(auth.authenticate(anyString())).thenReturn(user);

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(mock(Channel.class));
        when(connector.connect(eq(user), eq(did), eq(true), any(Version.class),
                any(ChannelPipeline.class)))
                .thenReturn(null);

        given()
                .header(Names.AUTHORIZATION, "Bearer foo")
                .header("Cookie", "route=" + did.toStringFormal())
                .header("Upload-ID", UniqueID.generate().toStringFormal())
        .expect()
                .statusCode(503)
        .when()
                .put("/");
    }

    public static class TestChannel extends AbstractChannel
    {
        private static class Address extends SocketAddress
        {
            private static final long serialVersionUID = 2271932999490284871L;
        }

        private static class Sink extends AbstractChannelSink
        {
            private final @Nullable  Consumer<ChannelEvent> _c;
            private final Executor _e = Executors.newSingleThreadExecutor();

            private Sink(@Nullable Consumer<ChannelEvent> c) {
                _c = c;
            }

            @Override
            public void eventSunk(ChannelPipeline p, ChannelEvent e) throws Exception
            {
                _e.execute(()-> {
                    if (e instanceof ChannelStateEvent) {
                        ChannelState state = ((ChannelStateEvent)e).getState();
                        Object value = ((ChannelStateEvent)e).getValue();
                        switch (NettyUtil.parseDownstreamEvent(state, value)) {
                            case CLOSE:
                                    ((TestChannel) p.getChannel()).onClose();
                                    e.getFuture().setSuccess();
                                break;
                            default:
                                break;
                        }
                    } else if (_c != null) {
                        _c.accept(e);
                        e.getFuture().setSuccess();
                    }
                });
            }

            @Override
            public ChannelFuture execute(ChannelPipeline pipeline, Runnable task)
            {
                ChannelFuture cf = Channels.future(pipeline.getChannel());
                _e.execute(() -> {
                    try {
                        task.run();
                        cf.setSuccess();
                    } catch (Throwable t) {
                        cf.setFailure(t);
                    }
                });
                return cf;
            }
        }

        private void onClose() {
            Channels.fireChannelDisconnected(this);
            Channels.fireChannelClosed(this);
            setClosed();
        }

        private final Address _addr = new Address();
        private final ChannelConfig _config = new DefaultChannelConfig();

        public TestChannel(ChannelPipeline pipeline, @Nullable Consumer<ChannelEvent> c)
        {
            super(null, null, pipeline, new Sink(c));
        }

        @Override
        public ChannelConfig getConfig()
        {
            return _config;
        }

        @Override
        public boolean isBound()
        {
            return !getCloseFuture().isDone();
        }

        @Override
        public boolean isConnected()
        {
            return !getCloseFuture().isDone();
        }

        @Override
        public void setInternalInterestOps(int ops)
        {
            super.setInternalInterestOps(ops);
        }

        @Override
        public SocketAddress getLocalAddress()
        {
            return _addr;
        }

        @Override
        public SocketAddress getRemoteAddress()
        {
            return _addr;
        }
    }

    private static Channel makeChannel(ChannelPipeline p, int ops, Consumer<Channel> init) {
        return makeChannel(p, ops, init, null);
    }

    private static Channel makeChannel(ChannelPipeline p, int ops, Consumer<Channel> init,
                                       Consumer<ChannelEvent> sink) {
        TestChannel c = new TestChannel(p, sink);
        c.setInternalInterestOps(ops);
        Channels.fireChannelOpen(c);
        c.getPipeline().execute(
                () -> Channels.fireChannelConnected(c, c.getRemoteAddress()));
        init.accept(c);
        return c;
    }

    @Test
    public void shouldReturn504OnUpstreamTimeout() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");

        when(auth.authenticate(anyString())).thenReturn(user);

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenAnswer(i -> makeChannel((ChannelPipeline) i.getArguments()[4], Channel.OP_READ,
                        c -> {}));

        given()
                .header(Names.AUTHORIZATION, "Bearer foo")
        .expect()
                .statusCode(504)
        .when()
                .get("/");
    }

    @Test
    public void shouldWaitForUpstreamToBeReadable() throws Exception
    {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");

        when(auth.authenticate(anyString())).thenReturn(user);

        DID did = DID.generate();
        ChannelBuffer response = ChannelBuffers.wrappedBuffer((
                "HTTP/1.1 204 No Content\r\n\r\n"
        ).getBytes(StandardCharsets.UTF_8));

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenAnswer(i -> makeChannel((ChannelPipeline) i.getArguments()[4], 0, c -> {
                    when(connector.device(c)).thenReturn(did);
                    when(connector.alternateDevices(c)).thenAnswer(dummy -> Stream.empty());

                    // wait 20 times the read timeout before producing response
                    timer.newTimeout(timeout -> {
                        ((TestChannel)c).setInternalInterestOps(Channel.OP_READ);
                        Channels.fireMessageReceived(c, response);
                    }, 20 * HttpRequestProxyHandler.READ_TIMEOUT, HttpRequestProxyHandler.TIMEOUT_UNIT);
                }));


        given()
                .header(Names.AUTHORIZATION, "Bearer foo")
        .expect()
                .statusCode(204)
                .header("Route", did.toStringFormal())
        .when()
                .get("/");
    }

    private final static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private static HttpResponse response(Object body) {
        byte[] b = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        DefaultHttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        resp.setChunked(false);
        resp.headers().add(Names.CONTENT_TYPE, "application/json");
        resp.headers().add(Names.CONTENT_LENGTH, b.length);
        resp.setContent(ChannelBuffers.wrappedBuffer(b));
        return resp;
    }

    public static class TestResponse {
        public final int id;
        TestResponse(int id) { this.id = id; }
        @Override
        public boolean equals(Object o) {
            return o instanceof TestResponse && id == ((TestResponse) o).id;
        }
        @Override
        public int hashCode() { return Integer.hashCode(id); }
    }

    private SimpleHttpClient<String, TestResponse> setup() throws Exception {
        AuthenticatedPrincipal user = new AuthenticatedPrincipal("foo@bar.baz");
        when(auth.authenticate(anyString())).thenReturn(user);

        when(connector.connect(eq(user), any(DID.class), eq(false), any(Version.class),
                any(ChannelPipeline.class)))
                .thenAnswer(i -> {
                    ChannelPipeline p = (ChannelPipeline)i.getArguments()[4];
                    // deal with Http objects instead of raw bytes
                    Assert.assertNotNull(p.remove(HttpClientCodec.class));
                    return makeChannel(p, Channel.OP_READ, c -> {
                        when(connector.device(c)).thenReturn((DID)i.getArguments()[1]);
                        when(connector.alternateDevices(c)).thenAnswer(dummy -> Stream.empty());
                    }, e -> {
                        if (e instanceof MessageEvent) {
                            HttpRequest req = (HttpRequest)((MessageEvent)e).getMessage();
                            Channels.fireMessageReceived(e.getChannel(),
                                    response(new TestResponse(e.getChannel().getId())));
                        }
                    });
                });

        return new SimpleHttpClient<String, TestResponse>(
                URI.create(RestAssured.baseURI + ":" + RestAssured.port),
                null, new NioClientSocketChannelFactory(Executors.newSingleThreadExecutor(),
                Executors.newSingleThreadExecutor(), 1), timer) {
            @Override
            public String buildURI(String query) {
                return _endpoint.getPath() + "/api/v1.0/files/" + query + "/content";
            }

            @Override
            public void modifyRequest(HttpRequest req, String query) {
                req.headers().set(Names.AUTHORIZATION, "Bearer foo");
            }
        };
    }

    @Test
    public void shouldReuseUpstreamForSameRoute() throws Exception {
        SimpleHttpClient<String, TestResponse> http = setup();

        DID did = DID.generate();
        l.info("did {}", did);

        when(router.route(anyString(), anyListOf(DID.class))).thenReturn(did);

        ListenableFuture<TestResponse> r1 = http.send(UniqueID.generate().toStringFormal());
        ListenableFuture<TestResponse> r2 = http.send(UniqueID.generate().toStringFormal());

        assertEquals(r1.get(), r2.get());
    }

    @Test
    public void shouldSwitchUpstreamForDifferentRoute() throws Exception {
        SimpleHttpClient<String, TestResponse> http = setup();

        DID d1 = DID.generate();
        DID d2 = DID.generate();
        UniqueID o1 = UniqueID.generate();
        UniqueID o2 = UniqueID.generate();
        l.info("o1 {} d1 {}", o1, d1);
        l.info("o2 {} d2 {}", o2, d2);

        when(router.route(contains(o1.toStringFormal()), anyListOf(DID.class))).thenReturn(d1);
        when(router.route(contains(o2.toStringFormal()), anyListOf(DID.class))).thenReturn(d2);

        ListenableFuture<TestResponse> r1 = http.send(o1.toStringFormal());
        ListenableFuture<TestResponse> r2 = http.send(o2.toStringFormal());

        assertNotEquals(r1.get(), r2.get());
    }

    @Test
    public void shouldWaitForUpstreamResponseBeforeSwitching() throws Exception {
        // TODO
    }
}
