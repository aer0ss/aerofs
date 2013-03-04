/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

public enum EventProperty {
    COUNT("count");

    private final String _name;

    EventProperty(final String name)
    {
        _name = name;
    }

    @Override
    public String toString()
    {
        return _name;
    }
}