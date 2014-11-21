/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base;

/**
 * Thread-safe just-in-time initialization.
 *
 * Particularly useful for global static variables because it avoid the plethora
 * of issues arising from the undefined initialization order
 *
 * Example usage:
 *
 * Lazy&lt;Foo&gt; FOO = new Lazy&lt;&gt;(() -> new Foo());
 *
 * ...
 *
 * FOO.get()
 */
public class Lazy<T> extends LazyChecked<T, Error>
{
    public Lazy(Creator<T, Error> c)
    {
        super(c);
    }
}
