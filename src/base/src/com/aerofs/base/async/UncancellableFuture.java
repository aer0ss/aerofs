/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.async;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;

import javax.annotation.Nullable;

/**
 * A specialization of {@link AbstractFuture} that can be set, but cannot be cancelled
 *
 * FIXME: I'm pretty sure this class violates the Liskov Substitution principle by turning off cancellation
 */
public class UncancellableFuture<V> extends AbstractFuture<V>
{
    private UncancellableFuture()
    {}

    //--------------------------------------------------------------------------
    //
    //
    // overridden cancellation methods
    //
    //
    //--------------------------------------------------------------------------

    /**
     * AeroFS operations can never be cancelled
     *
     * @return <code>false</code>
     */
    @Override
    public boolean isCancelled()
    {
        return false;
    }

    /**
     * AeroFS operations can never be cancelled
     *
     * @param mayInterruptIfRunning ignored parameter
     * @return <code>false</code>
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        return false;
    }


    //--------------------------------------------------------------------------
    //
    //
    // "setting" methods
    //
    // IMPORTANT: these methods are ripped directly from Guava's SettableFuture
    //
    //
    //--------------------------------------------------------------------------

    /**
     * Sets the value of this future.  This method will return {@code true} if the value was
     * successfully set, or {@code false} if the future has already been set or cancelled.
     *
     * @param value the value the future should hold
     * @return true if the value was successfully set
     */
    @Override
    public boolean set(@Nullable V value)
    {
        return super.set(value);
    }

    /**
     * Sets the future to having failed with the given exception. This exception will be wrapped in
     * an {@code ExecutionException} and thrown from the {@code get} methods. This method will
     * return {@code true} if the exception was successfully set, or {@code false} if the future has
     * already been set or cancelled.
     *
     * @param throwable the exception the future should hold.
     * @return true if the exception was successfully set
     */
    @Override
    public boolean setException(Throwable throwable)
    {
        return super.setException(throwable);
    }

    /**
     * Asynchronously propagates the result of the source future to this future
     * <p/>
     * See {@link FutureUtil#chain(UncancellableFuture, com.google.common.util.concurrent.ListenableFuture)}
     *
     * @param source The future from which to propagate the exception
     */
    public void chain(ListenableFuture<V> source)
    {
        FutureUtil.chain(this, source);
    }

    /**
     * Asynchronously propagates the exception set on the source future to this future
     * <p/>
     * See {@link FutureUtil#chainException(UncancellableFuture, com.google.common.util.concurrent.ListenableFuture)}
     *
     * @param source The future from which to propagate the exception
     */
    public void chainException(ListenableFuture<V> source)
    {
        FutureUtil.chainException(this, source);
    }

    /**
     * Create an instance of {@link UncancellableFuture}
     *
     * @param <V> Return value type of the wrapped operation
     * @return An instance of {@link UncancellableFuture}
     */
    public static <V> UncancellableFuture<V> create()
    {
        return new UncancellableFuture<V>();
    }

    /**
     * Create an instance of {@link UncancellableFuture} that is completed with the given result
     *
     * @param result Value to be set for the future
     * @param <V> Type of the result to set
     * @return An instance of {@link UncancellableFuture} immediately completed with a result
     */
    public static <V> UncancellableFuture<V> createSucceeded(@Nullable V result)
    {
        UncancellableFuture<V> retf = UncancellableFuture.create();
        retf.set(result);
        return retf;
    }

    /**
     * Create an instance of {@code UncancellableFuture} that is set to deliver an exception by
     * default (i.e. it indicates that the operation failed)
     *
     * @param throwable Cause of the future's failure
     * @param <V> Type of the future's result had it completed successfully
     * @return An instance of {@link UncancellableFuture} immediately failed with a cause
     */
    public static <V> UncancellableFuture<V> createFailed(Throwable throwable)
    {
        UncancellableFuture<V> retf = UncancellableFuture.create();
        retf.setException(throwable);
        return retf;
    }

    /**
     * Creates an {@link UncancellableFuture} in which an {@link AssertionError} is thrown if
     * the future completes successfully. The future returned is only ever meant to fail with
     * an exception
     *
     * @return An instance of {@link UncancellableFuture} that can not be set with a value, only an exception
     */
    public static <V> UncancellableFuture<V> createCloseFuture()
    {
        return new UncancellableFuture<V>()
        {
            @Override
            public boolean set(@Nullable V value)
            {
                throw new AssertionError("Close futures can not be set with values, only exceptions");
            }

        };
    }

}
