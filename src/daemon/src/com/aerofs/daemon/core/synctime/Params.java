/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.synctime;

import com.aerofs.base.C;

class Params
{
    // Maximum number of cells in the update times table
    static final int UPDATE_TIMES_TABLE_SIZE_UPPER_BOUND = 1000;

    static final long SEND_HISTOGRAM_INTERVAL = 1 * C.HOUR;
}
