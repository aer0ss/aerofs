/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib;

public interface IStartable
{
    /**
     * This method is not allowed to throw. Throwable tasks should be done elsewhere, for example,
     * in init_() methods.
     *
     * <strong>IMPORTANT:</strong> if this component has already been started
     * it will simply return (i.e. {@code start_()} will act as a no-op.
     */
    void start_();
}
