/*
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.lib;

import com.aerofs.daemon.lib.IDumpStat;
import com.aerofs.daemon.lib.IDumpStatMisc;

/**
 * Implemented by classes that want to provide both structured and unstructured
 * debugging information. This is a meta-interface that combines both
 * {@link IDumpStat} and {@link IDumpStatMisc}. Implementations are permitted to
 * block <strong>briefly</strong>.
 */
public interface IDebug extends IDumpStat, IDumpStatMisc
{
}
