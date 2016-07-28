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
import java.sql.Connection;

/**
 * Represents the various transaction isolation levels.
 * <br>
 * These levels are identical to those
 * in {@link Connection}, and are defined
 * within an enum to enforce type-safety.
 */
@SuppressWarnings("unused")
@Immutable
public enum TransactionIsolation {

    NONE(Connection.TRANSACTION_NONE),

    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),

    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),

    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),

    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final int level;

    /**
     * Constructor.
     *
     * @param level integer that corresponds to the equivalent
     *              transaction isolation level in {@code Connection}
     */
    TransactionIsolation(int level) {
        this.level = level;
    }

    /**
     * Get the integer that corresponds to the equivalent
     * transaction isolation level in {@link Connection}.
     *
     * @return integer that corresponds to the equivalent transaction isolation level in {@code Connection}
     */
    public int getLevel() {
        return level;
    }
}
