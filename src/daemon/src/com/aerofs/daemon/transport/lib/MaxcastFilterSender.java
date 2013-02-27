package com.aerofs.daemon.transport.lib;

import org.slf4j.Logger;

import com.aerofs.lib.Util;

public class MaxcastFilterSender {
    private static final Logger l = Util.l(MaxcastFilterSender.class);

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
