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

package com.aerofs.baseline.admin;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.Set;

/**
 * Stores a map of {@code health_check_name -> health_check}
 * defined for this service.
 */
@ThreadSafe
public final class RegisteredHealthChecks {

    private final Map<String, Object> healthChecks = Maps.newHashMap();

    /**
     * Check if any health checks were registered.
     *
     * @return {@code true} if at least one health
     * check was registered, {@code false} otherwise.
     */
    synchronized boolean hasHealthChecks() {
        return !healthChecks.isEmpty();
    }

    /**
     * Get an <em>immutable</em> copy of the health check
     * registration map as a set of {@link java.util.Map.Entry} instances.
     *
     * @return <em>immutable</em> copy of the health check registration map
     * as a set of {@code Entry} instances
     */
    synchronized Set<Map.Entry<String, Object>> getRegistrations() {
        return ImmutableSet.copyOf(healthChecks.entrySet());
    }

    /**
     * Add a command named {@code name} with implementation instance {@code healthCheck}.
     *
     * @param name unique name identifying this health check
     * @param healthCheck instance to be executed
     */
    public synchronized void register(String name, HealthCheck healthCheck) {
        registerInternal(name, healthCheck);
    }

    /**
     * Add a command named {@code name} with implementation class {@code healthCheck}.
     *
     * @param name unique name identifying this command
     * @param healthCheck implementation class to be located, loaded and executed.
     *                    This class does <strong>NOT</strong> handle locating
     *                    or loading the implementation class.
     */
    public synchronized void register(String name, Class<? extends HealthCheck> healthCheck) {
        registerInternal(name, healthCheck);
    }

    private void registerInternal(String name, Object healthCheck) {
        Object previous = healthChecks.putIfAbsent(name, healthCheck);
        Preconditions.checkState(previous == null, "previously registered health check\"%s\"", name);
    }
}
