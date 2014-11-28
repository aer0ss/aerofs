/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base.async;

import com.google.common.util.concurrent.*;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/**
 * Utility class that provides convenience methods for interacting with {@link ListenableFuture}s
 */
public abstract class FutureUtil
{
    /**
     * Asynchronously propagates the result of the source future to the destination future. If the source
     * future fails, so does the destination future
     *
     * @param destination The {@link UncancellableFuture} to propagate the result to
     * @param source The {@link ListenableFuture} to propagate the result from
     * @return The destination future
     */
    public static <V> UncancellableFuture<V> chain(final UncancellableFuture<V> destination,
            final ListenableFuture<V> source)
    {
        assert !destination.equals(source) : "chained futures must be unique";

        addCallback(source, new FutureCallback<V>()
        {
            @Override
            public void onSuccess(V v)
            {
                destination.set(v);
            }

            @Override
            public void onFailure(Throwable t)
            {
                destination.setException(t);
            }
        });

        return destination;
    }

    /**
     * Asynchronously propagates the failure of the source future to the destination future. If the source
     * future succeeds, the destination future remains untouched
     *
     * @param destination The {@link UncancellableFuture} to propagate the failure to
     * @param source The {@link ListenableFuture} to propagate the failure from
     * @return The destination future
     */
    public static UncancellableFuture<?> chainException(final UncancellableFuture<?> destination,
            final ListenableFuture<?> source)
    {
        assert !destination.equals(source) : "chained futures must be unique";

        addCallback(source, new FailedFutureCallback()
        {
            @Override
            public void onFailure(Throwable throwable)
            {
                destination.setException(throwable);
            }
        });

        return destination;
    }

    /**
     * Convenience method that calls {@link FutureUtil#addCallback(ListenableFuture, FutureCallback, Executor)}
     * with {@link com.google.common.util.concurrent.MoreExecutors#sameThreadExecutor()} as the executor on which
     * to execute the callback
     *
     * @param future The future to which to attach the callback
     * @param callback The callback to be run on future completion
     * @param <V> The type of the future's result
     */
    public static <V> void addCallback(ListenableFuture<V> future,
            FutureCallback<? super V> callback)
    {
        addCallback(future, callback, MoreExecutors.sameThreadExecutor());
    }

    /**
     * Attaches a {@link FutureCallback} to a given {@link ListenableFuture} that will run when the future is
     * completed. The callback will be executed on the specified {@link Executor}. If the future completes
     * successfully, then {@link FutureCallback#onSuccess(Object)} will be called, otherwise
     * {@link FutureCallback#onFailure(Throwable)} will be called with the Throwable that caused the future to fail.
     * <p/>
     * Example usage:
     * <pre>
     * ListenableFuture<Void> future = doAsyncTask();
     * FutureUtil.addCallback(future, new FutureCallback<Void>()
     * {
     *     public void onSuccess(Void result)
     *     {
     *         // Do something when the future successfully completes with result
     *     }
     *
     *     public void onFailure(Throwable cause)
     *     {
     *         // Error handling code goes here
     *     }
     * }, MoreExecutors.SameThreadExecutor());
     * </pre>
     * If any of the callback's methods throw a {@link RuntimeException}, that exception will not be caught by the
     * callback's caller. This is unlike Guava's {@link com.google.common.util.concurrent.Futures#addCallback(com.google.common.util.concurrent.ListenableFuture, com.google.common.util.concurrent.FutureCallback)}
     * method, which will catch exceptions from the {@link FutureCallback#onSuccess(Object)} and call {@link FutureCallback#onFailure(Throwable)}
     *
     * @param future The future to which to attach the callback
     * @param callback The callback to be run on future completion
     * @param executor The {@link Executor} on which to execute the callback
     * @param <V> The type of the future's result
     */
    public static <V> void addCallback(final ListenableFuture<V> future,
            final FutureCallback<? super V> callback, Executor executor)
    {
        assert callback != null;
        assert future != null;

        Runnable callbackListener = new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    // Attempt to extract the value of the future
                    // If an ExecutionException is thrown, then the future
                    // failed and the onFailure callback method is called.
                    // Otherwise, run the onSuccess callback method with
                    // the future's value.
                    // NOTE: If onSuccess throws a RuntimeException, allow
                    // it to propagate up the stack. This is the key reason
                    // we use this addCallback implementation over Guava's
                    // Futures.addCallback(). Their implementation catches
                    // all exceptions and calls onFailure(). This swallows
                    // AssertionErrors which is bad because we want AeroFS
                    // to die on AssertionError
                    V value = Uninterruptibles.getUninterruptibly(future);
                    callback.onSuccess(value);
                } catch (ExecutionException e) {
                    callback.onFailure(e.getCause());
                }
            }
        };
        future.addListener(callbackListener, executor);
    }
}
