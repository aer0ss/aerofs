/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.phy.block;

import java.io.IOException;

public interface IBlockStorageInitable
{
    public void init_(IBlockStorageBackend bsb) throws IOException;
}
