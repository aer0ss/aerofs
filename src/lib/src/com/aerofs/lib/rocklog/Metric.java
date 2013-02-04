/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import com.google.gson.Gson;

import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Maps.newHashMap;

public class Metric implements IRockLogMessage
{
    private static final String METRIC_KEY = "metrics";
    private static final String MESSAGE_NAME_KEY = "name";

    private final RockLog _rockLog;

    private Map<String, Object> _metadata = newHashMap();
    private Map<String, Object> _data = newHashMap();

    Metric (RockLog rockLog, String name)
    {
        this._rockLog = rockLog;

        this._metadata.putAll(new BaseMessage().getData());
        this._metadata.put(MESSAGE_NAME_KEY, name);
    }

    /**
     * Add an arbitrary piece of metadata to a metric.
     * <strong>IMPORTANT:</strong> if you add something with key {@value Metric#METRIC_KEY} it will
     * be overwritten by the data hash map
     * @return this {@code Metric} instance so that calls to addMetadata can be chained
     */
    public Metric addMetadata(String key, Object value)
    {
        checkArgument(!key.equalsIgnoreCase(METRIC_KEY), "cannot use " + METRIC_KEY + " as key");

        _metadata.put(key, value);

        return this;
    }

    public Metric addMetric(String key, Object value)
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

    @Override
    public String getJSON()
    {
        Map<String, Object> json = newHashMap();

        json.putAll(_metadata);
        if (!_data.isEmpty()) json.put(METRIC_KEY, _data);

        return new Gson().toJson(json);
    }

    @Override
    public String getURLPath()
    {
        return "/metrics";
    }
}
