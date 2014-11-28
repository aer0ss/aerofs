package com.aerofs.base;

import javax.annotation.Nonnull;

/**
 * Thread-safe just-in-time initialization with support for checked exceptions
 *
 * Particularly useful for global static variables because it avoid the plethora
 * of issues arising from the undefined initialization order
 *
 * Example usage:
 *
 * LazyChecked&lt;Foo,IOException&gt; FOO = new LazyChecked&lt;&gt;(() -> new Foo());
 *
 * ...
 *
 * FOO.get()
 */
public class LazyChecked<T, E extends Throwable>
{
    private volatile T _d;
    private final Creator<T, E> _c;

    @FunctionalInterface
    public interface Creator<T, E extends Throwable>
    {
        /**
         * Construct the actual instance of the object
         *
         * Called at most once (per AtomicInitializer instance) over the lifetime of the process
         */
        T create() throws E;
    }

    public LazyChecked(Creator<T, E> c)
    {
        _c = c;
    }

    public final @Nonnull T get() throws E
    {
        T t = _d;
        return t != null ? t : doubleChecked();
    }

    private synchronized @Nonnull T doubleChecked() throws E
    {
        T t = _d;
        return t != null ? t : (_d = _c.create());
    }
}