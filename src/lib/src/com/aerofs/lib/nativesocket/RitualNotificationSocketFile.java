/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.nativesocket;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.NativeSocketType;

import java.io.File;

public class RitualNotificationSocketFile
{
    public File get()
    {
        return new File(Cfg.nativeSocketFilePath(NativeSocketType.RITUAL_NOTIFICATION));
    }
}
