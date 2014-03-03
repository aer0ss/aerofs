/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.rocklog;

import com.aerofs.base.HttpServerTest;
import com.aerofs.base.HttpServerTest.RequestProcessor;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.testlib.AbstractTest;
import com.google.gson.Gson;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class TestRockLog extends AbstractTest
{
    private final static int TEST_PORT = 8082;
    private final static String TEST_URL = "http://localhost:" + TEST_PORT;
    private static final String DEFECT_NAME = "test.defect";
    private static final String DEFECT_MESSAGE = "Hello";

    @Mock InjectableCfg _cfg;

    HttpServerTest _server;

    @Before
    public void setUp()
    {
        _server = new HttpServerTest(TEST_PORT);
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
        _server.setRequestProcessor(new RequestProcessor()
        {
            @Override
            public HttpResponse process(HttpRequest request) throws Exception
            {
                // Decode the request
                assertEquals("/defects", request.getUri());
                assertEquals(HttpMethod.POST, request.getMethod());
                assertEquals("application/json", request.getHeader("Content-Type"));

                String json = new String(request.getContent().array());
                Map<String, Object> map = (Map<String, Object>)new Gson().fromJson(json, Map.class);

                // Check that everything was sent correctly
                assertEquals(DEFECT_NAME, map.get("name"));
                assertEquals(DEFECT_MESSAGE, map.get("@message"));

                // Send a success response
                return new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
            }
        });

        RockLog rockLog = new RockLog(TEST_URL, _cfg);

        Defect defect = rockLog.newDefect(DEFECT_NAME).setMessage(DEFECT_MESSAGE);
        boolean success = rockLog.rpc(defect);
        assertTrue(success);
    }

    @Test
    public void testReportServerFailure() throws ExecutionException, InterruptedException
    {
        _server.setRequestProcessor(new RequestProcessor()
        {
            @Override
            public HttpResponse process(HttpRequest request) throws Exception
            {
                // Return a 500 response
                return new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
        });

        RockLog rockLog = new RockLog(TEST_URL, _cfg);
        Defect defect = rockLog.newDefect(DEFECT_NAME).setMessage(DEFECT_MESSAGE);
        boolean success = rockLog.rpc(defect);
        assertFalse(success);
    }

    @Test
    public void shouldNotDieWhenSubmittingManyRequests()
    {
        _server.setRequestProcessor(new RequestProcessor()
        {
            @Override
            public HttpResponse process(HttpRequest request) throws Exception
            {
                // Return a 502 response
                return new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT);
            }
        });
        RockLog rockLog = new RockLog(TEST_URL, _cfg);
        for (int i = 0; i < 1000; ++i) rockLog.newDefect(DEFECT_NAME).send();
    }
}