/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.lib.cfg.InjectableCfg;

public class Event extends RockLogMessage
{
    private static final String EVENT_NAME_KEY = "event_name";

    Event(RockLog rockLog, InjectableCfg cfg, String eventName)
    {
        super(rockLog, cfg);

        addData(EVENT_NAME_KEY, eventName);
    }

    @Override
    String getURLPath()
    {
        return "/events";
    }
}
