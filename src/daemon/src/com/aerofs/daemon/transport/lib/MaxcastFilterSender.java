package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.lib.Util;
import org.slf4j.Logger;

public class MaxcastFilterSender {
    private static final Logger l = Loggers.getLogger(MaxcastFilterSender.class);

    private int _lastMCasttID;

    public MaxcastFilterSender()
    {
        _lastMCasttID = Util.rand().nextInt();
    }

    public int getNewMCastID_()
    {
        _lastMCasttID++;

        if (l.isDebugEnabled()) {
            l.debug("generate new mcastid: " + _lastMCasttID);
        }

        return _lastMCasttID;
    }
}
