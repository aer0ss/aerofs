/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;
import javax.annotation.Nullable;

/**
 * The absolute path to the autoexport folder, if one exists
 */
public class CfgAbsAutoExportFolder
{
    @Nullable public String get()
    {
        return Cfg.absAutoExportFolder();
    }
}
