/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.transfers.download.IDownloadCompletionListener;
import com.aerofs.daemon.core.tc.Token;

import javax.annotation.Nullable;
import java.util.Set;

interface IDownloadListenerFactory
{
    IDownloadCompletionListener create_(Set<DID> dids, @Nullable Token tk);
}