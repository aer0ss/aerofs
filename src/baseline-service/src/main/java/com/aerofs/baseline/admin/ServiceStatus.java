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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Map;

/**
 * Contains a list of all executed health checks
 * and their execution status.
 */
@SuppressWarnings("unused")
@NotThreadSafe
public final class ServiceStatus {

    private final Map<String, Status> consolidated;

    public ServiceStatus() {
        this.consolidated = Maps.newHashMap();
    }

    /**
     * Constructor.
     *
     * @param consolidated mapping of all executed health checks and their associated execution status
     */
    @JsonCreator
    public ServiceStatus(@JsonProperty("consolidated") Map<String, Status> consolidated) {
        this.consolidated = consolidated;
    }

    /**
     * Add an executed health check and its associated execution status.
     *
     * @param name health check name
     * @param status execution status
     */
    public void addStatus(String name, Status status) {
        Object previous = consolidated.putIfAbsent(name, status);
        Preconditions.checkState(previous == null, "previously added status for \"%s\"", name);
    }

    /**
     * @return mapping of all executed health checks and their associated execution status
     */
    public Map<String, Status> getConsolidated() {
        return consolidated;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ServiceStatus other = (ServiceStatus) o;
        return consolidated.equals(other.consolidated);
    }

    @Override
    public int hashCode() {
        return consolidated.hashCode();
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("consolidated", consolidated)
                .toString();
    }
}
