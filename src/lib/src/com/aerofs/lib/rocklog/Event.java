/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import com.google.gson.Gson;

import java.util.HashMap;

public class Event implements IRockLogMessage
{
    private HashMap<String, Object> _json = new HashMap<String, Object>();
    private final RockLog _rocklog;

    Event(RockLog rocklog, String name)
    {
        _json.putAll(new BaseMessage().getData());

        _rocklog = rocklog;
        _json.put("event_name", name);
    }

    @Override
    public String getJSON()
    {
        return new Gson().toJson(_json);
    }

    @Override
    public String getURLPath()
    {
        return "/events";
    }

    public void send()
    {
        _rocklog.send(this);
    }

    public void sendAsync()
    {
        _rocklog.sendAsync(this);
    }
}
