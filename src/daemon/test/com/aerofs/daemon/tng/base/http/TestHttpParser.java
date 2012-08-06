/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.http;

import com.aerofs.daemon.tng.base.http.HttpMessage.Method;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestHttpParser extends AbstractTest
{
    HttpParser parser;

    @Before
    public void setUp() throws Exception
    {
        parser = new HttpParser();
    }

    @Test
    public void shouldParseResponseHeader() throws Exception
    {
        byte[] data = "HTTP/1.1 200 Ok\nHeader: Value\nHeader2: Value2\n\n".getBytes("UTF-8");

        HttpMessage message = parser.parse(new ByteArrayInputStream(data));

        assertNotNull(message);
        assertEquals(200, message.getResponseCode());
        assertEquals("Value", message.getHeader("Header"));
        assertEquals("Value2", message.getHeader("Header2"));
    }

    @Test
    public void shouldParseRequestHeaderInMultipleChunks() throws Exception
    {
        byte[] data = "CONNECT http://www.google.com HTTP/1.1\nHeader: Value\nHeader2: Value2\n\n".getBytes("UTF-8");

        HttpMessage message = parser.parse(new ByteArrayInputStream(data));

        assertNotNull(message);
        assertEquals(Method.CONNECT, message.getMethod());
        assertEquals("http://www.google.com", message.getUri().toString());
        assertEquals("Value", message.getHeader("Header"));
        assertEquals("Value2", message.getHeader("Header2"));
    }

    @Test(expected = IOException.class)
    public void shouldThrowIOExceptionWhenParsingBadData() throws Exception
    {
        byte[] data = "dsfjl;sdjflsgjlgjlsd\nfgjdflgjlfgjk fjsl;g fgjs fg\nfg fgkshj fkgjhs g".getBytes("UTF-8");

        parser.parse(new ByteArrayInputStream(data));
    }
}
