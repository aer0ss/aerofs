/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.defects;

import com.aerofs.base.ElapsedTimer;
import com.aerofs.base.Loggers;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.cfg.InjectableCfg;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.Executor;

import static com.aerofs.defects.DefectUtils.newDefectID;

// TODO: a better way to handle this
public class FrequentDefect extends Defect
{
    private static final Logger l = Loggers.getLogger(FrequentDefect.class);

    private int _count;
    private ElapsedTimer _timer;

    public FrequentDefect(String name, InjectableCfg cfg, RockLog rockLog, DryadClient dryad,
            Executor executor, RecentExceptions recentExceptions, Map<String, String> properties)
    {
        super(name, cfg, rockLog, dryad, executor, recentExceptions, properties);
    }

    @Override
    public void sendSync()
            throws Exception
    {
        _count++;

        if (_timer == null || _timer.elapsed() > LibParam.FREQUENT_DEFECT_SENDER_INTERVAL) {
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
