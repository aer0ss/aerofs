/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.download;

import com.aerofs.daemon.core.collector.IExPermanentError;

/**
 * Exception thrown when a meta/meta conflict cannot be resolved locally
 */
public class ExUnsolvedMetaMetaConflict extends Exception implements IExPermanentError
{
    private static final long serialVersionUID = -7851884541582231076L;
}
