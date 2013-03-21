/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

/**
 * For various technical reasons, mostly related to profiling, it is nice to group related threads
 * in ThreadGroups.
 */
public class TransportThreadGroup
{
    private static ThreadGroup _transportThreadGroup = new ThreadGroup("transport");

    public static ThreadGroup get() { return _transportThreadGroup; }
}
