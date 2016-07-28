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

import javax.annotation.concurrent.Immutable;

@Immutable
public abstract class RequestProperties {

    public static final String REQUEST_CONTEXT_CHANNEL_ID_PROPERTY = "channel-id";
    public static final String REQUEST_CONTEXT_REQUEST_ID_PROPERTY = "request-id";

    private RequestProperties() {
        // to prevent instantiation by subclasses
    }
}
