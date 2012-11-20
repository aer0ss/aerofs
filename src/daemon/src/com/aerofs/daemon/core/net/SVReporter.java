/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.tng.IDefectReporter;
import com.aerofs.sv.client.SVClient;

import javax.annotation.Nullable;

public final class SVReporter implements IDefectReporter
{
    @Override
    public void reportDefect(String message, @Nullable Throwable cause)
    {
        if (cause != null) {
            SVClient.logSendDefectAsync(true, message, cause);
        } else {
            SVClient.logSendDefectAsync(true, message);
        }
    }
}