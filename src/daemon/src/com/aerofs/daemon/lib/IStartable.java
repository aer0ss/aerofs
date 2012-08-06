/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib;

public interface IStartable
{
    /**
     * <strong>IMPORTANT:</strong> if this component has already been started
     * it will simply return (i.e. {@code start_()} will act as a NOOP
     */
    void start_();
}
