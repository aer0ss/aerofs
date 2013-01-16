/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.db;

import com.aerofs.lib.ex.ExBadArgs;

public class DBSearchUtil
{
    public static void throwOnInvalidOffset(int offset)
            throws ExBadArgs
    {
        if (offset < 0) throw new ExBadArgs("offset is negative");
    }

    // To avoid DoS attacks, do not permit listUsers queries to exceed 1000 returned results
    private static final int ABSOLUTE_MAX_RESULTS = 1000;

    public static void throwOnInvalidMaxResults(int maxResults)
            throws ExBadArgs
    {
        if (maxResults > ABSOLUTE_MAX_RESULTS) throw new ExBadArgs("maxResults is too big");
        else if (maxResults < 0) throw new ExBadArgs("maxResults is a negative number");
    }
}
