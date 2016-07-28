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

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.logging.ConsoleLoggingConfiguration;
import com.aerofs.baseline.logging.FileLoggingConfiguration;
import com.aerofs.baseline.logging.LoggingConfiguration;

/**
 * Instantiation of the baseline {@code Configuration} object.
 * Does not add any extra configuration blocks.
 */
public final class ServiceConfiguration extends Configuration {

    private static final HttpConfiguration ADMIN = new HttpConfiguration();
    static {
        ADMIN.setHost("localhost");
        ADMIN.setPort((short) 8888);
        ADMIN.setDirectMemoryBacked(false);
        ADMIN.setNumRequestProcessingThreads(1);
    }

    private static final HttpConfiguration SERVICE = new HttpConfiguration();
    static {
        SERVICE.setHost("0.0.0.0");
        SERVICE.setPort((short) 9999);
        SERVICE.setDirectMemoryBacked(false);
        SERVICE.setNumRequestProcessingThreads(1);
    }

    private static final ConsoleLoggingConfiguration CONSOLE = new ConsoleLoggingConfiguration();
    static {
        CONSOLE.setEnabled(true);
    }

    private static final FileLoggingConfiguration FILE = new FileLoggingConfiguration();
    static {
        FILE.setEnabled(false);
    }

    private static final LoggingConfiguration LOGGING = new LoggingConfiguration();
    static {
        LOGGING.setLevel("DEBUG");
        LOGGING.setConsole(CONSOLE);
        LOGGING.setFile(FILE);
    }

    public static final ServiceConfiguration TEST_CONFIGURATION = new ServiceConfiguration();
    static {
        TEST_CONFIGURATION.setAdmin(ADMIN);
        TEST_CONFIGURATION.setService(SERVICE);
        TEST_CONFIGURATION.setLogging(LOGGING);
    }

    public static final String ADMIN_URL = String.format("http://%s:%d", ADMIN.getHost(), ADMIN.getPort());

    public static final String SERVICE_URL = String.format("http://%s:%d", SERVICE.getHost(), SERVICE.getPort());
}
