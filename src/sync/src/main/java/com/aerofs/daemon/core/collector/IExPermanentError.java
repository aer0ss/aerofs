/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

/**
 * Permanent Errors relate to the Collector algorithm, specifically whether a device should be
 * re-tried to download a particular component. When a device responds to a component request
 * with a permanent error, the collector should not try to download the object from that device
 * again, until the state of that device changes (e.g. it sends a new Bloom filter that contains the
 * object).
 */
public interface IExPermanentError
{
}
