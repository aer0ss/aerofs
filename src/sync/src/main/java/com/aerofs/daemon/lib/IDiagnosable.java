/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.lib;

import com.google.protobuf.Message;

/**
 * Implemented by components that provide a means to dump diagnostics information.
 */
public interface IDiagnosable
{
    /**
     * Dump diagnostics information for the component. This method
     * <strong>may</strong> or <strong>may not</strong> be thread-safe.
     *
     * @return a {@code Protobuf} object with diagnostics information.
     */
    Message dumpDiagnostics_();
}
