/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core;

/**
 * Implementations of this interface sets additional core event handlers that aree specific to
 * single-user or multi-user systems.
 */
public interface IMultiplicityEventHandlerSetter
{
    void setHandlers_(CoreEventDispatcher dispatcher);
}
