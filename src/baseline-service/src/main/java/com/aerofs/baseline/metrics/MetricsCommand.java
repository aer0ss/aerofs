/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.metrics;

import com.aerofs.baseline.admin.Command;
import com.aerofs.baseline.admin.Commands;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.ws.rs.core.MultivaluedMap;
import java.io.PrintWriter;
import java.util.Map;

@ThreadSafe
public final class MetricsCommand implements Command {

    private final ObjectMapper mapper;

    @Inject
    public MetricsCommand(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void execute(MultivaluedMap<String, String> queryParameters, PrintWriter entityWriter) throws Exception {
        MetricRegistry registry = MetricRegistries.getRegistry();

        ObjectNode root = mapper.createObjectNode();

        for (Map.Entry<String, Counter> entry : registry.getCounters().entrySet()) {
            ObjectNode node = getParent(root, entry.getKey());
            node.put("count", entry.getValue().getCount());
        }

        for (Map.Entry<String, Gauge> entry : registry.getGauges().entrySet()) {
            ObjectNode node = getParent(root, entry.getKey());
            node.putPOJO("value", entry.getValue().getValue());
        }

        for (Map.Entry<String, Histogram> entry : registry.getHistograms().entrySet()) {
            ObjectNode node = getParent(root, entry.getKey());
            dumpSnapshot(entry.getValue().getSnapshot(), node);
        }

        for (Map.Entry<String, Meter> entry : registry.getMeters().entrySet()) {
            ObjectNode node = getParent(root, entry.getKey());
            dumpMetered(node, entry.getValue());
        }

        for (Map.Entry<String, Timer> entry : registry.getTimers().entrySet()) {
            ObjectNode node = getParent(root, entry.getKey());
            dumpMetered(node, entry.getValue());
            dumpSnapshot(entry.getValue().getSnapshot(), node);
        }

        Object serialized = mapper.treeToValue(root, Object.class);
        Commands.outputFormattedJson(mapper, entityWriter, queryParameters, serialized);
    }

    private ObjectNode getParent(ObjectNode root, String key) {
        ObjectNode node = root;

        for (String component : key.split("\\.")) {
            ObjectNode nextNode = (ObjectNode) node.get(component);

            if (nextNode == null) {
                nextNode = mapper.createObjectNode();
                node.set(component, nextNode);
            }

            node = nextNode;
        }

        return node;
    }

    private static void dumpSnapshot(Snapshot histogram, ObjectNode node) {
        node.put("min", histogram.getMin());
        node.put("max", histogram.getMax());
        node.put("mean", histogram.getMean());
        node.put("median", histogram.getMedian());
        node.put("std-dev", histogram.getStdDev());
        node.put("75th-percentile", histogram.get75thPercentile());
        node.put("95th-percentile", histogram.get95thPercentile());
        node.put("98th-percentile", histogram.get98thPercentile());
        node.put("99th-percentile", histogram.get99thPercentile());
        node.put("999th-percentile", histogram.get999thPercentile());
    }

    private static void dumpMetered(ObjectNode node, Metered meter) {
        node.put("count", meter.getCount());
        node.put("15min-rate", meter.getFifteenMinuteRate());
        node.put("5min-rate", meter.getFiveMinuteRate());
        node.put("1min-rate", meter.getOneMinuteRate());
        node.put("mean-rate", meter.getMeanRate());
    }
}
