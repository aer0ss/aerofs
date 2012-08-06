/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tap;

import com.aerofs.testlib.AbstractTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;

import static com.aerofs.testlib.FutureAssert.getFutureResult;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestLinkedNonblockingQueue extends AbstractTest
{
    LinkedNonblockingQueue<String> queue;

    @Before
    public void setUp() throws Exception
    {
        queue = new LinkedNonblockingQueue<String>();
    }

    @Test
    public void shouldWaitWhenCallingTakeUntilSomethingIsAdded()
    {
        final String EXPECTED_MESSAGE = "Hey there";

        ListenableFuture<String> result = queue.takeWhenAvailable();

        assertFalse(result.isDone());

        queue.offer(EXPECTED_MESSAGE);

        assertTrue(result.isDone());
        String message = getFutureResult(result);
        assertEquals(EXPECTED_MESSAGE, message);
    }

    @Test
    public void shouldImmediatelySetValueWhenQueueIsNotEmpty()
    {
        final String EXPECTED_MESSAGE = "Hey there";

        queue.offer(EXPECTED_MESSAGE);

        ListenableFuture<String> result = queue.takeWhenAvailable();

        assertTrue(result.isDone());
        String message = getFutureResult(result);
        assertEquals(EXPECTED_MESSAGE, message);
    }

    @Test
    public void shouldQueueSeveralElementsAndGiveThemAwayInTheSameOrder()
    {
        final ImmutableList<String> EXPECTED_MESSAGES = ImmutableList.of("one", "two", "three");

        for (String message : EXPECTED_MESSAGES) {
            queue.offer(message);
        }

        for (int i = 0; i < EXPECTED_MESSAGES.size(); i++) {
            ListenableFuture<String> result = queue.takeWhenAvailable();
            assertTrue(result.isDone());
            assertEquals(EXPECTED_MESSAGES.get(i), getFutureResult(result));
        }

        // Assert there is nothing left
        ListenableFuture<String> result = queue.takeWhenAvailable();
        assertFalse(result.isDone());
    }

    @Test
    public void shouldFillFuturesAsElementsComeIn()
    {
        final ImmutableList<String> EXPECTED_MESSAGES = ImmutableList.of("one", "two", "three");
        final LinkedList<ListenableFuture<String>> futures = new LinkedList<ListenableFuture<String>>();

        for (int i = 0; i < EXPECTED_MESSAGES.size(); i++) {
            ListenableFuture<String> result = queue.takeWhenAvailable();
            assertFalse(result.isDone());
            futures.add(result);
        }

        for (String message : EXPECTED_MESSAGES) {
            queue.offer(message);
            ListenableFuture<String> result = futures.removeFirst();
            assertTrue(result.isDone());
            assertEquals(message, getFutureResult(result));
        }

        assertTrue(futures.isEmpty());

        // Assert there is nothing left
        ListenableFuture<String> result = queue.takeWhenAvailable();
        assertFalse(result.isDone());
    }
}
