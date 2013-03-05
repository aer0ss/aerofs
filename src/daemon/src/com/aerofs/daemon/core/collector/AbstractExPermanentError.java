/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.ex.AbstractExWirable;
import com.aerofs.proto.Common.PBException;

/**
 * Permanent Errors relate to the Collector algorithm, specifically whether a device should be
 * re-tried to download a particular componenent. When a device responds to a componenent request
 * with a permanent error, the collector should not try to download the object from that device
 * again, until the state of that device changes (e.g. it sends a new Bloom filter that contains the
 * object).
 */
public abstract class AbstractExPermanentError extends AbstractExWirable
{
    private static final long serialVersionUID = 1L;

    public AbstractExPermanentError() { super(); }

    public AbstractExPermanentError(String msg) { super(msg); }

    public AbstractExPermanentError(Exception cause) { super(cause); }

    public AbstractExPermanentError(PBException pb) { super(pb); }
}
