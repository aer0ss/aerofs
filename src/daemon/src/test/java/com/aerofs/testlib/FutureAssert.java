/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.testlib;

import com.aerofs.base.async.UncancellableFuture;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

public abstract class FutureAssert
{
    private FutureAssert()
    {}

    public static void assertCompletionFutureChainedProperly(UncancellableFuture<?> operationFuture,
            ListenableFuture<?> chainedFuture)
    {
        assertFalse(operationFuture.isDone());
        assertFalse(chainedFuture.isDone());

        operationFuture.set(null);

        assertTrue(operationFuture.isDone());
        assertTrue(chainedFuture.isDone());
    }

    /**
     * Returns the Throwable that caused the given future to fail. Throws an AssertionError if
     * the future did not fail with an exception
     * @param future The future who's Throwable to return
     * @return The Throwable that caused the future to fail
     */
    public static Throwable getFutureThrowable(ListenableFuture<?> future)
    {
        try {
            future.get();
            fail("Future did not throw an exception");
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (Exception e) {
            fail("Unexpected exception thrown: " + e);
        }
        return null;
    }

    /**
     * Returns the value of the given future. Throws an AssertionError if the future failed
     * @param future The future from which to retrieve the value
     * @return The value the future was set to
     */
    @Nullable
    public static <FutureResultType> FutureResultType getFutureResult(ListenableFuture<FutureResultType> future)
    {
        try {
            return future.get();
        } catch (ExecutionException e) {
            fail("Future failed with exception: " + e.getCause());
        } catch (Exception e) {
            fail("Unexpected exception thrown: " + e);
        }
        return null;
    }

    /**
     * Asserts that the future throws an exception of the type specified, with the message given
     * @param future The future to check
     * @param clazz The type of the exception
     * @param message The message the exception must have
     */
    public static void assertThrows(ListenableFuture<?> future, Class<? extends Exception> clazz, String message)
    {
        Throwable cause = getFutureThrowable(future);
        String expectedName = clazz.getSimpleName();
        String actualName = cause.getClass().getSimpleName();
        assertTrue("Expected exception of type " + expectedName + " but got " + actualName + " instead", clazz.isInstance(cause));
        if (message != null) {
            assertEquals("Expected exception to have message '" + message + "' but had '" + cause.getMessage() + "' instead", message, cause.getMessage());
        }
    }

    /**
     * Asserts that the future throws an exception of the type specified
     * @param future The future to check
     * @param clazz The type of the exception
     */
    public static <T> void assertThrows(ListenableFuture<T> future, Class<? extends Exception> clazz)
    {
        assertThrows(future, clazz, null);
    }

    /**
     * Asserts that the future completed successfully and doesn't throw an
     * exception
     * @param future The future to check
     */
    public static void assertNoThrow(ListenableFuture<?> future)
    {
        getFutureResult(future);
    }
}
