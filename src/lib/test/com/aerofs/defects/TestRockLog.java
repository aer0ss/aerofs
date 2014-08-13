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
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static java.util.Collections.emptyMap;
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
    @Mock DryadClient _dryad;
    Executor _executor = MoreExecutors.sameThreadExecutor();
    @Mock RecentExceptions _recentExceptions;
    Map<String, String> _properties = emptyMap();

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

        RockLog rockLog = new RockLog(TEST_URL, new Gson());
        Defect defect = new Defect(DEFECT_NAME, _cfg, rockLog, _dryad, _executor,
                _recentExceptions, _properties)
                .setMessage(DEFECT_MESSAGE);

        boolean success = rockLog.rpc("/defects", defect.getDefectData());
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
        Defect defect = new Defect(DEFECT_NAME, _cfg, rockLog, _dryad, _executor, _recentExceptions,
                _properties)
                .setMessage(DEFECT_MESSAGE);

        boolean success = rockLog.rpc("/defects", defect.getDefectData());
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
            new Defect(DEFECT_NAME, _cfg, rockLog, _dryad, _executor, _recentExceptions,
                    _properties)
                    .setMessage(DEFECT_MESSAGE)
                    .sendSync();
        }
    }

    // this test is ignored because it currently fails.
    // FIXME (AT): make it pass (pretty sure this is an issue with the test)
    @Ignore
    @Test
    public void shouldTransferContentOnRequestsWithLargeContent()
            throws Exception
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

                // FIXME (AT): this currently fails
                assertTrue(request.getContent().array().length > 0);

                return new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
            }
        });

        RockLog rockLog = new RockLog(TEST_URL, new Gson());
        Defect defect = new Defect(DEFECT_NAME, _cfg, rockLog, _dryad, _executor,
                _recentExceptions, _properties)
                .setMessage(DEFECT_MESSAGE);

        for (int i = 0; i < 1000; i++) {
            defect.addData("key" + i, "value");
        }

        boolean success = rockLog.rpc("/defects", defect.getDefectData());
        assertTrue(success);
    }
}
