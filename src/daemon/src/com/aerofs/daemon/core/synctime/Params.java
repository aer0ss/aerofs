/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.C;
import com.netflix.config.DynamicStringProperty;

class Params
{
    // Maximum number of cells in the update times table
    static final int UPDATE_TIMES_TABLE_SIZE_UPPER_BOUND = 1000;

    static final long SEND_HISTOGRAM_INTERVAL = 1 * C.HOUR;

    // TODO (MJ) could make this a DynamicUrlProperty, but I'm concerned it wouldn't take well
    // to an empty string in that case
    static final DynamicStringProperty SERVER_URL =
            new DynamicStringProperty("synctime.server.url", "http://tts.aerofs.com");
}
