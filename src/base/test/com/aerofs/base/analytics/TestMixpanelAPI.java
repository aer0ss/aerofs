/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.base.analytics;

import com.aerofs.base.Base64;
import com.aerofs.base.TestHttpServer;
import com.aerofs.base.TestHttpServer.RequestProcessor;
import com.beust.jcommander.internal.Maps;
import com.google.common.base.Charsets;
import com.google.gson.Gson;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class TestMixpanelAPI
{
    private static final int TEST_PORT = 8081;
    private static final String TEST_API_ENDPOINT = "http://localhost:" + TEST_PORT  + "?data=";
    private static final String TEST_TOKEN = "00000000000000000000000000000000";
    private static final String TEST_EVENT = "test event";
    private static final String TEST_USER = "test_user";

    TestHttpServer _server;

    @Before
    public void setUp()
    {
        _server = new TestHttpServer(TEST_PORT);
        _server.startAndWait();
    }

    @After
    public void tearDown() throws Exception
    {
        _server.shutDown();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldMakeWellFormedRequest() throws Exception
    {
        final MixpanelAPI mixpanelAPI = new MixpanelAPI(TEST_TOKEN, TEST_API_ENDPOINT);

        // Setup the server to check that the client's request is well formed
        _server.setRequestProcessor(new RequestProcessor()
        {
            @Override
            public HttpResponse process(HttpRequest request)
                    throws Exception
            {
                // Decode the request
                assertTrue(request.getUri().startsWith("?data="));
                String base64 = request.getUri().substring("?data=".length());
                String json = new String(Base64.decode(base64), Charsets.UTF_8);
                Map<String, Object> map = (Map<String, Object>)new Gson().fromJson(json, Map.class);
                Map<String, Object> properties = (Map<String, Object>)map.get("properties");

                // Check that everything was sent correctly
                assertEquals(TEST_EVENT, map.get("event"));
                assertEquals(TEST_USER, properties.get("distinct_id"));
                assertEquals(TEST_TOKEN, properties.get("token"));
                assertEquals("world", properties.get("hello"));

                // Send a success response
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
                response.setContent(ChannelBuffers.copiedBuffer("1", Charsets.UTF_8));
                return response;
            }
        });

        // Send the request to the server
        Map<String, String> properties = Maps.newHashMap();
        properties.put("hello", "world");
        mixpanelAPI.track(TEST_EVENT, TEST_USER, properties).get();

        mixpanelAPI.close();
        mixpanelAPI.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void testReportServerFailure() throws ExecutionException, InterruptedException
    {
        final MixpanelAPI mixpanelAPI = new MixpanelAPI(TEST_TOKEN, TEST_API_ENDPOINT);

        // Setup the server so that it will respond with a page with the character "0" - Mixpanel's
        // way of indicating failures
        _server.setRequestProcessor(new RequestProcessor()
        {
            @Override
            public HttpResponse process(HttpRequest request)
                    throws Exception
            {
                HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
                response.setContent(ChannelBuffers.copiedBuffer("0", Charsets.UTF_8));
                return response;
            }
        });

        // Try to track an event and ensure we report a failure
        try {
            mixpanelAPI.track(TEST_EVENT, TEST_USER, null).get();
            fail();
        } catch (ExecutionException e) {
            assertEquals(IOException.class, e.getCause().getClass());
        }

        mixpanelAPI.close();
        mixpanelAPI.awaitTermination(5, TimeUnit.SECONDS);
    }
}
