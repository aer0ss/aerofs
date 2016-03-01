/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.daemon.core.tc.Token;

/**
 * Interface used to simplify the process of following a dependency chain when downloading an object
 */
public interface IDownloadContext
{
    Token token();
}
