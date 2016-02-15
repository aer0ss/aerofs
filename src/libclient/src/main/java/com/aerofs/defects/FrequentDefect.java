/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgVer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.Executor;

import static com.aerofs.defects.DefectUtils.newDefectID;

public class FrequentDefect extends AutoDefect
{
    private static final Logger l = Loggers.getLogger(FrequentDefect.class);

    private int _count;
    private ElapsedTimer _timer;

    public FrequentDefect(String name, RockLog rockLog, DryadClient dryad,
            Executor executor, RecentExceptions recentExceptions, Map<String, String> properties,
            CfgLocalUser cfgLocalUser, CfgLocalDID cfgLocalDID, String rtroot, CfgVer cfgVer)
    {
        super(name, rockLog, dryad, executor, recentExceptions, properties,
                cfgLocalUser, cfgLocalDID, rtroot, cfgVer);
    }

    @Override
    public void sendSync()
            throws Exception
    {
        _count++;

        if (_timer == null || _timer.elapsed() > ClientParam.FREQUENT_DEFECT_SENDER_INTERVAL) {
            // reset the timer now because the send may fail
            if (_timer == null) {
                _timer = new ElapsedTimer();
            }
            _timer.restart();

            this.setDefectID(newDefectID())
                    .setMessage(_message + " (" + _count + ")")
                    .setException(_exception)
                    .addData("count", _count);

            super.sendSync();

            // reset count only if the send succeeds
            _count = 0;
        } else {
            l.warn("frequent defect: " + _message + ": " + _exception);
        }
    }
}
