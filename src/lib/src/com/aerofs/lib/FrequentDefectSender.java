/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import com.aerofs.sv.client.SVClient;

public class FrequentDefectSender
{
    private static final Logger l = Loggers.getLogger(FrequentDefectSender.class);

    private int _count;
    private boolean _reportedADefectYet = false;
    private ElapsedTimer _timer;

    public FrequentDefectSender()
    {
        _count = 0;
        _reportedADefectYet = false;
        _timer = new ElapsedTimer();
    }

    public synchronized void logSendAsync(String desc)
    {
        logSendAsync(desc, null);
    }

    public synchronized void logSendAsync(String desc, Exception e)
    {
        _count++;
        if (!_reportedADefectYet ||
                _timer.elapsed() > LibParam.FREQUENT_DEFECT_SENDER_INTERVAL) {
            _reportedADefectYet = true;
            _timer.restart();
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
