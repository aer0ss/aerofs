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
import java.util.concurrent.TimeUnit;

/**
 * HTTP-processing-specific properties and their values.
 */
@Immutable
abstract class Constants {

    // network
    public static final int DEFAULT_MAX_ACCEPT_QUEUE_SIZE = 1024;

    // http
    public static final long DEFAULT_IDLE_TIMEOUT = TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS);
    public static final int HTTP_MAX_INITIAL_LINE_LENGTH = 256;
    public static final int HTTP_MAX_HEADER_SIZE = 1024; // 1K
    public static final int HTTP_MAX_CHUNK_SIZE = 4 * 1024 * 1024; // 4K
    public static final int ENTITY_UNREAD_BYTES_LOW_WATERMARK = 128;
    public static final int ENTITY_UNREAD_BYTES_HIGH_WATERMARK = 1024; // 1K

    // netty threading
    public static final int DEFAULT_NUM_BOSS_THREADS = 2;
    public static final int DEFAULT_NUM_NETWORK_IO_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    public static final int DEFAULT_NUM_REQUEST_PROCESSING_THREADS = Runtime.getRuntime().availableProcessors();

    private Constants() {
        // to prevent instantiation by subclasses
    }
}
