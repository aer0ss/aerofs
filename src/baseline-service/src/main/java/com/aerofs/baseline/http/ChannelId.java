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

import javax.annotation.Nullable;

/**
 * Type-safe representation of the netty
 * channel id over which the HTTP request was
 * received.
 */
public final class ChannelId {

    private final String value;

    /**
     * Constructor.
     *
     * @param value netty channel id over which the HTTP request was received
     */
    public ChannelId(String value) {
        this.value = value;
    }

    /**
     * Get netty channel id over which the HTTP request was received.
     *
     * @return netty channel id over which the HTTP request was received
     */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChannelId other = (ChannelId) o;
        return Objects.equal(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("value", value)
                .toString();
    }
}
