/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class Metrics extends RockLogMessage
{
    private static final String METRICS_BASE_KEY = "metrics";

    private final Map<String, Object> _metricsMap = newHashMap();

    Metrics(RockLog rockLog)
    {
        super(rockLog);

        //
        // add the containing "metrics-only" map to the underlying data map
        // this puts the container in the underlying data store; further operations to Metrics
        // operate on this "metrics-only" map. Also, we don't have to repeatedly add it
        //

        addData(METRICS_BASE_KEY, _metricsMap);
    }

    public Metrics addMetric(String key, Object value)
    {
        _metricsMap.put(getFullyQualifiedMetricName(key), value);

        return this;
    }

    private String getFullyQualifiedMetricName(String key)
    {
        return getRockLog().getPrefix() + "." + key;
    }

    @Override
    String getURLPath()
    {
        return "/metrics";
    }
}
