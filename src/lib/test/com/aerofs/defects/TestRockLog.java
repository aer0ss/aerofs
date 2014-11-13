/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.HttpServerTest;
import com.aerofs.base.HttpServerTest.RequestProcessor;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.testlib.AbstractTest;
import com.google.common.util.concurrent.MoreExecutors;
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

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestRockLog extends AbstractTest
{
    private static final int TEST_PORT = 8082;
    private static final String TEST_URL = "http://localhost:" + TEST_PORT;
    private static final String DEFECT_NAME = "defect.test";
    private static final String DEFECT_MESSAGE = "Hello";

    @Mock InjectableCfg _cfg;
    Executor _executor = MoreExecutors.sameThreadExecutor();

    HttpServerTest _server;

    @Before
    public void setUp()
    {
        // even though we are posting over https on HC, it's not meaningful for us to spin up a
        // https server for test purpose. The main reason for that is the SSL handshakes are handled
        // by nginx, not Java, in HC. So we don't actually improve coverage by running a https
        // server in this test.
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
        _server.setRequestProcessor(request -> {
            // Decode the request
            assertEquals("/defects", request.getUri());
            assertEquals(HttpMethod.POST, request.getMethod());
            assertEquals("application/json", request.headers().get("Content-Type"));

            String json = request.getContent().toString(Charset.forName("ISO-8859-1"));
            Map<String, Object> map = (Map<String, Object>)new Gson().fromJson(json, Map.class);

            // Check that everything was sent correctly
            assertEquals(DEFECT_NAME, map.get("name"));
            assertEquals(DEFECT_MESSAGE, map.get("@message"));

            // Send a success response
            return new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
        });

        RockLog rockLog = new RockLog(TEST_URL, new Gson());
        Defect defect = new Defect(DEFECT_NAME, _cfg, rockLog, _executor)
                .setMessage(DEFECT_MESSAGE);

        boolean success = rockLog.rpc("/defects", defect.getData());
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

        com.aerofs.defects.RockLog rockLog = new com.aerofs.defects.RockLog(TEST_URL, new Gson());
        Defect defect = new Defect(DEFECT_NAME, _cfg, rockLog, _executor)
                .setMessage(DEFECT_MESSAGE);

        boolean success = rockLog.rpc("/defects", defect.getData());
        assertFalse(success);
    }

    @Test
    public void shouldNotDieWhenSubmittingManyRequests()
            throws Exception
    {
        _server.setRequestProcessor(new RequestProcessor()
        {
            @Override
            public HttpResponse process(HttpRequest request) throws Exception
            {
                // Return a 504 response
                return new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.GATEWAY_TIMEOUT);
            }
        });
        RockLog rockLog = new RockLog(TEST_URL, new Gson());

        for (int i = 0; i < 1000; i++) {
            new Defect(DEFECT_NAME, _cfg, rockLog, _executor)
                    .setMessage(DEFECT_MESSAGE)
                    .sendSync();
        }
    }
}
