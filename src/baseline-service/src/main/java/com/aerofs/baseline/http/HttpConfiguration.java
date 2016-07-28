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

package com.aerofs.baseline.http;

import com.google.common.base.Objects;
import org.hibernate.validator.constraints.NotBlank;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.validation.constraints.Min;

@SuppressWarnings("unused")
@NotThreadSafe
public final class HttpConfiguration {

    @NotBlank
    private String host;

    @Min(0)
    private short port;

    private boolean directMemoryBacked = true;

    private boolean useDefaultExceptionMappers = true;

    @Min(1)
    private long idleTimeout = com.aerofs.baseline.http.Constants.DEFAULT_IDLE_TIMEOUT;

    @Min(1)
    private int maxAcceptQueueSize = com.aerofs.baseline.http.Constants.DEFAULT_MAX_ACCEPT_QUEUE_SIZE;

    @Min(1)
    private int numNetworkThreads = com.aerofs.baseline.http.Constants.DEFAULT_NUM_NETWORK_IO_THREADS;

    @Min(1)
    private int numRequestProcessingThreads = com.aerofs.baseline.http.Constants.DEFAULT_NUM_REQUEST_PROCESSING_THREADS;

    private boolean enabled = true;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public short getPort() {
        return port;
    }

    public void setPort(short port) {
        this.port = port;
    }

    public boolean isDirectMemoryBacked()
    {
        return directMemoryBacked;
    }

    public void setDirectMemoryBacked(boolean directMemoryBacked)
    {
        this.directMemoryBacked = directMemoryBacked;
    }

    public boolean getUseDefaultExceptionMappers()
    {
        return useDefaultExceptionMappers;
    }

    public void setUseDefaultExceptionMappers(boolean useDefaultExceptionMappers)
    {
        this.useDefaultExceptionMappers = useDefaultExceptionMappers;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public int getMaxAcceptQueueSize() {
        return maxAcceptQueueSize;
    }

    public void setMaxAcceptQueueSize(int maxAcceptQueueSize) {
        this.maxAcceptQueueSize = maxAcceptQueueSize;
    }

    public int getNumNetworkThreads() {
        return numNetworkThreads;
    }

    public void setNumNetworkThreads(int numNetworkThreads) {
        this.numNetworkThreads = numNetworkThreads;
    }

    public int getNumRequestProcessingThreads() {
        return numRequestProcessingThreads;
    }

    public void setNumRequestProcessingThreads(int numRequestProcessingThreads) {
        this.numRequestProcessingThreads = numRequestProcessingThreads;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HttpConfiguration other = (HttpConfiguration) o;
        return Objects.equal(host, other.host)
                && port == other.port
                && directMemoryBacked == other.directMemoryBacked
                && idleTimeout == other.idleTimeout
                && maxAcceptQueueSize == other.maxAcceptQueueSize
                && numNetworkThreads == other.numNetworkThreads
                && numRequestProcessingThreads == other.numRequestProcessingThreads
                && enabled == other.enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(host, port, directMemoryBacked, idleTimeout, maxAcceptQueueSize, numNetworkThreads, numRequestProcessingThreads, enabled);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("host", host)
                .add("port", port)
                .add("directMemoryBacked", directMemoryBacked)
                .add("idleTimeout", idleTimeout)
                .add("maxAcceptQueueSize", maxAcceptQueueSize)
                .add("numNetworkThreads", numNetworkThreads)
                .add("numRequestProcessingThreads", numRequestProcessingThreads)
                .add("enabled", enabled)
                .toString();
    }
}
