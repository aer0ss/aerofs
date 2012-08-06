/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base;

import com.aerofs.daemon.tng.base.pipeline.IStateContainer;
import com.aerofs.lib.id.DID;

public interface IPeer
{
    DID getDID_();

    IStateContainer getPeerStateContainer_();
}
