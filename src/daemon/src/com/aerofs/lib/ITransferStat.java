/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

/**
 * For transfer statistics which can be accessed cheaply and in a thread-safe way
 */
public interface ITransferStat
{
    public long bytesIn();
    public long bytesOut();
}
