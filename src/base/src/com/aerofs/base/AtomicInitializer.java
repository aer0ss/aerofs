package com.aerofs.base;

import javax.annotation.Nonnull;

/**
 * Thread-safe just-in-time initialization.
 *
 * Particularly useful for global static variables because it avoid the plethora
 * of issues arising from the undefined initialization order
 *
 * Example usage:
 *
 * AtomicInitializer&lt;Foo&gt; FOO = new AtomicInitializer&lt;Foo&gt;() {
 *     @Override
 *     public Foo create()
 *     {
 *         new Foo();
 *     }
 * }
 *
 * ...
 *
 * FOO.get()
 */
public abstract class AtomicInitializer<T>
{
    private volatile T _d;

    public final @Nonnull T get()
    {
        T t = _d;
        return t != null ? t : doubleChecked();
    }

    private synchronized @Nonnull T doubleChecked()
    {
        T t = _d;
        return t != null ? t : (_d = create());
    }

    /**
     * Construct the actual instance of the object
     *
     * Called at most once over the lifetime of the process
     */
    protected abstract @Nonnull T create();
}