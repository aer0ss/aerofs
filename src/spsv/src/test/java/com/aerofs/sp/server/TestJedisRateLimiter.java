/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.servlets.lib.db.AbstractJedisTest;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestJedisRateLimiter extends AbstractJedisTest
{
    JedisRateLimiter _limiter;

    @Before
    public void setUp() throws Exception
    {
        _limiter = new JedisRateLimiter(getTransaction(), 3, 5000L, "rl");
    }

    @Test
    public void testSingleRequest() throws Exception
    {
        assertFalse(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));
    }

    @Test
    public void testConcurrentRequests() throws Exception
    {
        // should allow three requests
        assertFalse(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertFalse(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertFalse(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));

        // should not allow further requests
        assertTrue(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertTrue(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertTrue(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));
    }

    @Test
    public void testWindowEnforced() throws Exception
    {
        // should allow three requests in five seconds
        assertFalse(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertFalse(_limiter.updateAtTime(1400000004000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertFalse(_limiter.updateAtTime(1400000004500L, "1.2.3.4", "jonathan@aerofs.com"));

        // should not allow a fourth request in five seconds
        assertTrue(_limiter.updateAtTime(1400000004800L, "1.2.3.4", "jonathan@aerofs.com"));

        // should allow a request six seconds after the first
        assertFalse(_limiter.updateAtTime(1400000006000L, "1.2.3.4", "jonathan@aerofs.com"));

        // should not allow another request immediately after
        assertTrue(_limiter.updateAtTime(1400000006010L, "1.2.3.4", "jonathan@aerofs.com"));
    }

    @Test
    public void testWindowExpires() throws Exception
    {
        // should allow three requests in five seconds
        assertFalse(_limiter.updateAtTime(1400000000000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertFalse(_limiter.updateAtTime(1400000004000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertFalse(_limiter.updateAtTime(1400000004500L, "1.2.3.4", "jonathan@aerofs.com"));

        // should not allow a fourth request in five seconds
        assertTrue(_limiter.updateAtTime(1400000004800L, "1.2.3.4", "jonathan@aerofs.com"));

        // should allow three requests ten seconds after the first
        assertFalse(_limiter.updateAtTime(1400000010000L, "1.2.3.4", "jonathan@aerofs.com"));
        assertFalse(_limiter.updateAtTime(1400000010010L, "1.2.3.4", "jonathan@aerofs.com"));
        assertFalse(_limiter.updateAtTime(1400000010020L, "1.2.3.4", "jonathan@aerofs.com"));

        // should not allow another request immediately after three conformant ones
        assertTrue(_limiter.updateAtTime(1400000010030L, "1.2.3.4", "jonathan@aerofs.com"));
    }
}

