/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import com.google.gson.Gson;

import java.util.HashMap;

public class Metric implements IRockLogMessage
{
    private HashMap<String, Object> _metaData = new HashMap<String, Object>();
    private HashMap<String, Object> _data = new HashMap<String, Object>();
    private final RockLog _rockLog;

    Metric (RockLog rockLog, String name) {
        _rockLog = rockLog;
        _metaData.putAll(new BaseMessage().getData());
        _metaData.put("name", name);
    }

    /**
     * Add an arbitrary piece of metadata to a metric.
     * NB: if you add something with key "data", it will be overwritten by the data hash map
     * @param key
     * @param value
     * @return
     */
    public Metric addMetaData(String key, Object value)
    {
        _metaData.put(key, value);
        return this;
    }

    public Metric addData(String key, Object value)
    {
        _data.put(key, value);
        return this;
    }

    public void send()
    {
        _rockLog.send(this);
    }

    public void sendAsync()
    {
        _rockLog.sendAsync(this);
    }

    public String getJSON()
    {
        HashMap<String, Object> json = new HashMap<String, Object>();
        json.putAll(_metaData);
        json.put("data", _data);
        return new Gson().toJson(json);
    }

    public String getURLPath()
    {
        return "/metrics";
    }
}
