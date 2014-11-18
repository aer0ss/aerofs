/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.injectable;

/**
 * A class to facilitate injection of time providers, rather than static dependencies
 * on System.currentTimeMillis().  One is more testable than the other.
 */
public class TimeSource
{
    public long getTime() { return System.currentTimeMillis(); }
}
