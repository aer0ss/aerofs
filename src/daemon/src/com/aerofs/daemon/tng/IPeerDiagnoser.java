/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng;

import com.aerofs.base.id.DID;
import com.google.common.util.concurrent.ListenableFuture;

/**
 */
public interface IPeerDiagnoser
{
    ListenableFuture<Void> processDiagnosisPacket(DID did);
}
