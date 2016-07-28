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

package com.aerofs.baseline.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Minimal logging layout that encodes only:
 * <ul>
 *     <li>Level</li>
 *     <li>UTC ISO 8601 date</li>
 *     <li>Message and/or exception</li>
 * </ul>
 */
@ThreadSafe
final class MinimalLayout extends PatternLayout {

    MinimalLayout(LoggerContext context) {
        super();
        setOutputPatternAsHeader(false);
        setPattern("%-5p [%d{ISO8601,UTC}]: %m%n%rEx");
        setContext(context);
    }
}
