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

package com.aerofs.baseline;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Utility methods for creating and interacting with threads.
 */
@ThreadSafe
public abstract class Threads {

    /**
     * Create a new {@link java.util.concurrent.ThreadFactory} with
     * threads named according to {@code nameFormat}.
     * have names
     *
     * @param nameFormat a {@link String#format(String, Object...)}-compatible
     *                   format String, to which a unique, sequential integer (0, 1, etc.)
     *                   will be supplied as the single parameter
     * @return a valid {@code ThreadFactory} with threads whose {@link Thread#getName}
     * returns the name specified by {@code nameFormat}
     *
     * @see com.google.common.util.concurrent.ThreadFactoryBuilder#setNameFormat
     */
    public static ThreadFactory newNamedThreadFactory(String nameFormat) {
        ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
        return builder.setNameFormat(nameFormat).setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler()).setThreadFactory(Executors.defaultThreadFactory()).build();
    }

    private Threads() {
        // to prevent instantiation by subclasses
    }
}
