/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.C;
import com.aerofs.lib.injectable.TimeSource;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestRecentExceptions
{
    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String PROGRAM_NAME = "test";

    private final TimeSource ts = mock(TimeSource.class);

    @Test
    public void shouldSaveToDisk() throws IOException
    {
        long mockTime = System.currentTimeMillis();
        when(ts.getTime()).thenReturn(mockTime);
        // Open the rex file and clear it
        RecentExceptions rex = new RecentExceptions(PROGRAM_NAME, TMP_DIR, 1 * C.HOUR, ts);
        rex.clear();

        Exception ex = new Exception();

        // This newly-created exception shouldn't be recent
        assertFalse(rex.isRecent(ex));

        // Add it to the recent exceptions list, now isRecent should be true
        rex.add(ex);
        assertTrue(rex.isRecent(ex));

        // Re-open the file with a different instance, check that the exception is still recent
        RecentExceptions rex2 = new RecentExceptions(PROGRAM_NAME, TMP_DIR, 1 * C.HOUR, ts);
        assertTrue(rex2.isRecent(ex));
    }

    @Test
    public void shouldKeepOnlyMostRecent() throws IOException, InterruptedException
    {
        long then = System.currentTimeMillis();

        // Create a bunch of exceptions, all on different lines, so that they all get different stack traces
        List<Exception> exceptions = Lists.newArrayList();
        exceptions.add(new Exception("e00"));
        exceptions.add(new Exception("e01"));
        exceptions.add(new Exception("e02"));
        exceptions.add(new Exception("e03"));
        exceptions.add(new Exception("e04"));
        exceptions.add(new Exception("e05"));
        exceptions.add(new Exception("e06"));
        exceptions.add(new Exception("e07"));
        exceptions.add(new Exception("e08"));
        exceptions.add(new Exception("e09"));
        exceptions.add(new Exception("e10"));
        exceptions.add(new Exception("e11"));

        // Open the rex file, clear it, and all 12 exceptions
        RecentExceptions rex = new RecentExceptions(PROGRAM_NAME, TMP_DIR, 1 * C.HOUR, ts);
        rex.clear();
        long now = then;
        for (Exception e : exceptions) {
            // Ensure all exceptions have different timestamps
            when(ts.getTime()).thenReturn(now++);
            rex.add(e);
        }

        // Now the first 2 (the oldest ones) shouldn't be in the recent list anymore since we limit
        // the recent exceptions to 10
        assertFalse(rex.isRecent(exceptions.get(0)));
        assertFalse(rex.isRecent(exceptions.get(1)));

        // The remaining 10 should be in the list
        for (int i = 2; i < exceptions.size(); i++) {
            assertTrue(rex.isRecent(exceptions.get(i)));
        }
    }

    @Test
    public void shouldNoLongerBeRecentAfterIntervalPasses() throws IOException, InterruptedException
    {
        long backThen = System.currentTimeMillis();
        when(ts.getTime()).thenReturn(backThen);
        // Open the rex file with a short 10 ms interval
        RecentExceptions rex = new RecentExceptions(PROGRAM_NAME, TMP_DIR, 10 * C.SEC, ts);
        rex.clear();

        Exception e = new Exception();

        // Add a new exception, make sure it is considered recent
        rex.add(e);
        assertTrue(rex.isRecent(e));

        // Advance time past the recent interval, check that the exception is no longer recent
        long now = backThen + 15 * C.SEC;
        when(ts.getTime()).thenReturn(now);
        assertFalse(rex.isRecent(e));
    }}
