/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import org.apache.log4j.Logger;

public class MaxcastFilterSender {
    private static final Logger l = Util.l(MaxcastFilterSender.class);

    private int _lastMCasttID;

    public MaxcastFilterSender()
    {
        _lastMCasttID = SecUtil.newRandomInt();
    }

    public int getNewMCastID_()
    {
        _lastMCasttID++;
        if (l.isDebugEnabled()) l.debug("generate new mcastid: " + _lastMCasttID);

        return _lastMCasttID;
    }
}
