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

import javax.annotation.concurrent.Immutable;
import java.util.concurrent.TimeUnit;

/**
 * Defaults for the database configuration values in {@link DatabaseConfiguration}.
 */
@Immutable
public abstract class DatabaseConstants {

    /**
     * Default transaction isolation level (SERIALIZABLE).
     */
    public static final TransactionIsolation DEFAULT_TRANSACTION_ISOLATION = TransactionIsolation.SERIALIZABLE;

    /**
     * Auto-commit is disabled by default.
     */
    public static final boolean DEFAULT_AUTO_COMMIT_ENABLED = false;

    /**
     * Database connections are <strong>not</strong> checked
     * for liveness when returned to the connection pool.
     */
    public static final boolean DEFAULT_CHECK_CONNECTION_ON_RETURN = false;

    /**
     * Database connections are <strong>not</strong> checked
     * for liveness when borrowed from the connection pool.
     */
    public static final boolean DEFAULT_CHECK_CONNECTION_ON_BORROW = false;

    /**
     * Minimum number of database connections that are
     * created and placed into the connection pool by default.
     */
    public static final int DEFAULT_MIN_TOTAL_CONNECTIONS = 10;

    /**
     * Maximum number of database connections that are
     * created and placed into the connection pool by default.
     * <br>
     * Given that the default minimum and maximum are the
     * same value, the pool <strong>should</strong> have a constant size.
     */
    public static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 10;

    /**
     * Maximum amount of time to wait to acquire
     * a database connection from the connection pool.
     */
    public static final long DEFAULT_ACQUIRE_CONNECTION_TIMEOUT = TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS);

    /**
     * Maximum amount of time to wait for a response
     * from the database to <strong>any</strong> query.
     */
    public static final long DEFAULT_QUERY_TIMEOUT = TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS);

    /**
     * Maximum number of unused database connections that
     * are allowed to remain in the connection pool.
     */
    public static final int DEFAULT_MAX_IDLE_CONNECTIONS = 10;

    /**
     * How long a database connection can remain unused for
     * before it is considered an 'idle' connection.
     */
    public static final long DEFAULT_IDLE_CONNECTION_TIMEOUT = TimeUnit.MILLISECONDS.convert(60, TimeUnit.SECONDS);

    /**
     * Database connections are checked
     * for liveness if they are idle.
     */
    public static final boolean DEFAULT_CHECK_CONNECTION_WHEN_IDLE = true;

    /**
     * Interval at which connections are checked to see if they are idle,
     * and if so, whether they should be removed from the connection pool.
     */
    public static final long DEFAULT_IDLE_CONNECTION_HEALTH_CHECK_TIMEOUT = TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES);

    /**
     * Default liveness-check query.
     */
    public static final String DEFAULT_VALIDATION_QUERY = "/* DB HEALTH CHECK */ SELECT 1";

    /**
     * Maximum amount of time to wait for a response
     * to the liveness-check query.
     */
    public static final long DEFAULT_VALIDATION_QUERY_TIMEOUT = DEFAULT_QUERY_TIMEOUT;

    private DatabaseConstants() {
        // to prevent instantiation by subclasses
    }
}
