/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core;


import com.aerofs.lib.IDumpStatMisc;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * Avoid circular deps...
 */
public class Dumpables
{
    public static final Map<String, IDumpStatMisc> ALL = Maps.newConcurrentMap();

    public static void add(String s, IDumpStatMisc o)
    {
        ALL.put(s, o);
    }

    private Dumpables() {}
}
