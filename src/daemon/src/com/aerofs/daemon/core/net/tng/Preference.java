/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net.tng;

import java.util.Comparator;

/**
 * Rates a transport implementation in terms of desirability. The preference domain is
 * from 0 to INT_MAX, with 0 being the most preferable and larger values being less preferable.
 */
public class Preference
{
    private final int _pref;

    public Preference(int pref)
    {
        assert pref >= 0;
        _pref = pref;
    }

    public int pref()
    {
        return _pref;
    }

    //
    // types
    //

    public static class DefaultComparator implements Comparator<Preference>
    {
        @Override
        public int compare(Preference p1, Preference p2)
        {
            return p1.pref() - p2.pref();
        }

        public static DefaultComparator DEFAULT_COMPARATOR = new DefaultComparator();
    }
}
