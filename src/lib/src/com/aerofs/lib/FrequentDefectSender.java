/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import org.slf4j.Logger;

import com.aerofs.sv.client.SVClient;

public class FrequentDefectSender
{
    private static final Logger l = Util.l(FrequentDefectSender.class);

    private long _lastSend;
    private int _count;

    public synchronized void logSendAsync(String desc)
    {
        logSendAsync(desc, null);
    }

    public synchronized void logSendAsync(String desc, Exception e)
    {
        _count++;
        long now = System.currentTimeMillis();
        if (now > _lastSend + Param.FREQUENT_DEFECT_SENDER_INTERVAL) {
            _lastSend = now;
            SVClient.logSendDefectAsync(true, desc + " (" + _count + ")", e);
            // Count should display the diff since the last FDS,
            // otherwise if the daemon crashes between defect reports, we can't tell if the
            // number reported is a running tally or diff
            _count = 0;
        } else {
            // avoid Util.e(e) to reduce the size of logs
            l.warn("fds: " + desc + (e == null ? "" : ": " + e));
        }
    }
}
