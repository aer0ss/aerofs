/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.download;

import com.aerofs.daemon.core.download.dependence.DependencyEdge.DependencyType;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.id.SOCID;

/**
 * Interface used to simplify the process of following a dependency chain when downloading an object
 */
public interface IDownloadContext
{
    Token token();

    public void downloadSync_(SOCID socid, DependencyType type) throws Exception;

    boolean hasResolved_(SOCID socid);
}
