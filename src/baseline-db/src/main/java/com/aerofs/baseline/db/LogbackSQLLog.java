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

package com.aerofs.baseline.db;

import ch.qos.logback.classic.Logger;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.logging.FormattedLog;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;

/**
 * JDBI logger that logs messages to the baseline service log.
 * <br>
 * Logging is only enabled at <strong>TRACE</strong> level or lower.
 */
@ThreadSafe
final class LogbackSQLLog extends FormattedLog {

    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(DBI.class);

    LogbackSQLLog() { }

    @Override
    protected boolean isEnabled() {
        return LOGGER.isTraceEnabled();
    }

    @Override
    protected void log(String msg) {
        LOGGER.trace(msg);
    }
}
