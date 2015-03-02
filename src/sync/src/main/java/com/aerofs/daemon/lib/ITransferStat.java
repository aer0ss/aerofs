/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.lib;

/**
 * For transfer statistics which can be accessed cheaply and in a thread-safe way
 */
public interface ITransferStat
{
    long bytesIn();
    long bytesOut();
}
