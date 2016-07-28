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

import com.codahale.metrics.MetricRegistry;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public abstract class MetricRegistries {

    // static holder pattern
    // allows for lazy initialization of a MetricRegistry instance
    private static class MetricsHolder {
        private static final MetricRegistry METRIC_REGISTRY = new MetricRegistry();
    }

    public static MetricRegistry getRegistry() {
        return MetricsHolder.METRIC_REGISTRY;
    }

    public static String name(String name, @Nullable String ... names) {
        return MetricRegistry.name(name, names);
    }

    public static void unregisterMetrics() {
        MetricRegistry registry = MetricRegistries.getRegistry();
        registry.removeMatching((name, metric) -> true);
    }

    private MetricRegistries() {
        // to prevent instantiation by subclasses
    }
}
