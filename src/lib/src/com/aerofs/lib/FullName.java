/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

/**
 * This class represents a user's full name
 */
public class FullName
{
    final public String _first;
    final public String _last;

    public FullName(String first, String last)
    {
        assert first != null;
        assert last != null;
        _first = first;
        _last = last;
    }

    /**
     * @return a string with first and last name combined
     */
    public String combine()
    {
        // call trim() in case the first or last name is empty.
        String ret = (_first + " " + _last).trim();
        return ret.isEmpty() ? "Unknown User" : ret;
    }
}
