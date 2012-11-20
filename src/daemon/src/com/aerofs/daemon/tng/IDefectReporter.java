/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import javax.annotation.Nullable;

/**
 * To be implemented by any provider that allows Transport classes to log defects
 */
public interface IDefectReporter
{
    /**
     * Report a defect
     * <p/>
     * Where a defect is reported to does not matter (to a log, to a remote server, etc.)
     * <p/>
     * <strong>IMPORTANT:</strong> implementers <strong>must</strong> be thread-safe
     * @param message a short description or message to send with the defect
     * @param cause the exception that triggered the defect report (may be {@code null})
     */
    void reportDefect(String message, @Nullable Throwable cause);
}
