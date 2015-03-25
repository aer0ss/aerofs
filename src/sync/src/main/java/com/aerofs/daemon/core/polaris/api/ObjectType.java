/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.api;

import com.aerofs.base.NoObfuscation;

@NoObfuscation
public enum ObjectType
{
    ROOT,
    FILE,
    FOLDER,
    MOUNT_POINT
}