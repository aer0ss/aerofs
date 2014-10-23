/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.submit;

import com.aerofs.daemon.core.polaris.async.AsyncTaskCallback;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;

public interface Submitter
{
    String name();

    void submit_(SIndex sidx, AsyncTaskCallback cb) throws SQLException;
}
