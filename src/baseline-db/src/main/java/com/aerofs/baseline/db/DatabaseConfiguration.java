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

import com.google.common.base.Objects;
import org.hibernate.validator.constraints.NotBlank;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Represents all the values that can be used to
 * configure the service's database connection pool.
 */
@SuppressWarnings("unused")
@NotThreadSafe
public final class DatabaseConfiguration {

    @NotBlank
    private String driverClass;

    @NotBlank
    private String url;

    private @Nullable String username = null;

    private @Nullable String password = null;

    @NotNull
    private TransactionIsolation defaultTransactionIsolation = DatabaseConstants.DEFAULT_TRANSACTION_ISOLATION;

    private boolean autoCommit = DatabaseConstants.DEFAULT_AUTO_COMMIT_ENABLED;

    private boolean checkConnectionOnBorrow = DatabaseConstants.DEFAULT_CHECK_CONNECTION_ON_BORROW;

    private boolean checkConnectionOnReturn = DatabaseConstants.DEFAULT_CHECK_CONNECTION_ON_RETURN;

    @Min(0)
    private int minTotalConnections = DatabaseConstants.DEFAULT_MIN_TOTAL_CONNECTIONS;

    @Min(1)
    private int maxTotalConnections = DatabaseConstants.DEFAULT_MAX_TOTAL_CONNECTIONS;

    @Min(-1)
    private long acquireConnectionTimeout = DatabaseConstants.DEFAULT_ACQUIRE_CONNECTION_TIMEOUT;

    @Min(-1)
    private int queryTimeout = (int) DatabaseConstants.DEFAULT_QUERY_TIMEOUT;

    @Min(0)
    private int maxIdleConnections = DatabaseConstants.DEFAULT_MAX_IDLE_CONNECTIONS;

    @Min(0)
    private long closeIdleConnectionAfter = DatabaseConstants.DEFAULT_IDLE_CONNECTION_TIMEOUT;

    private boolean checkConnectionWhenIdle = DatabaseConstants.DEFAULT_CHECK_CONNECTION_WHEN_IDLE;

    @Min(0)
    private long idleConnectionCheckInterval = DatabaseConstants.DEFAULT_IDLE_CONNECTION_HEALTH_CHECK_TIMEOUT;

    @NotBlank
    private String validationQuery = DatabaseConstants.DEFAULT_VALIDATION_QUERY;

    @Min(-1)
    private long validationQueryTimeout = DatabaseConstants.DEFAULT_VALIDATION_QUERY_TIMEOUT;

    public String getDriverClass() {
        return driverClass;
    }

    public void setDriverClass(String driverClass) {
        this.driverClass = driverClass;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public @Nullable String getUsername() {
        return username;
    }

    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    public @Nullable String getPassword() {
        return password;
    }

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public TransactionIsolation getDefaultTransactionIsolation() {
        return defaultTransactionIsolation;
    }

    public void setDefaultTransactionIsolation(TransactionIsolation defaultTransactionIsolation) {
        this.defaultTransactionIsolation = defaultTransactionIsolation;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public boolean isCheckConnectionOnBorrow() {
        return checkConnectionOnBorrow;
    }

    public void setCheckConnectionOnBorrow(boolean checkConnectionOnBorrow) {
        this.checkConnectionOnBorrow = checkConnectionOnBorrow;
    }

    public boolean isCheckConnectionOnReturn() {
        return checkConnectionOnReturn;
    }

    public void setCheckConnectionOnReturn(boolean checkConnectionOnReturn) {
        this.checkConnectionOnReturn = checkConnectionOnReturn;
    }

    public int getMinTotalConnections() {
        return minTotalConnections;
    }

    public void setMinTotalConnections(int minTotalConnections) {
        this.minTotalConnections = minTotalConnections;
    }

    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public long getAcquireConnectionTimeout() {
        return acquireConnectionTimeout;
    }

    public void setAcquireConnectionTimeout(long acquireConnectionTimeout) {
        this.acquireConnectionTimeout = acquireConnectionTimeout;
    }

    public int getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    public void setMaxIdleConnections(int maxIdleConnections) {
        this.maxIdleConnections = maxIdleConnections;
    }

    public long getCloseIdleConnectionAfter() {
        return closeIdleConnectionAfter;
    }

    public void setCloseIdleConnectionAfter(long closeIdleConnectionAfter) {
        this.closeIdleConnectionAfter = closeIdleConnectionAfter;
    }

    public boolean isCheckConnectionWhenIdle() {
        return checkConnectionWhenIdle;
    }

    public void setCheckConnectionWhenIdle(boolean checkConnectionWhenIdle) {
        this.checkConnectionWhenIdle = checkConnectionWhenIdle;
    }

    public long getIdleConnectionCheckInterval() {
        return idleConnectionCheckInterval;
    }

    public void setIdleConnectionCheckInterval(long idleConnectionCheckInterval) {
        this.idleConnectionCheckInterval = idleConnectionCheckInterval;
    }

    public String getValidationQuery() {
        return validationQuery;
    }

    public void setValidationQuery(String validationQuery) {
        this.validationQuery = validationQuery;
    }

    public long getValidationQueryTimeout() {
        return validationQueryTimeout;
    }

    public void setValidationQueryTimeout(int validationQueryTimeout) {
        this.validationQueryTimeout = validationQueryTimeout;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DatabaseConfiguration other = (DatabaseConfiguration) o;

        return Objects.equal(autoCommit, other.autoCommit) &&
                Objects.equal(checkConnectionOnBorrow, other.checkConnectionOnBorrow) &&
                Objects.equal(checkConnectionOnReturn, other.checkConnectionOnReturn) &&
                Objects.equal(minTotalConnections, other.minTotalConnections) &&
                Objects.equal(maxTotalConnections, other.maxTotalConnections) &&
                Objects.equal(acquireConnectionTimeout, other.acquireConnectionTimeout) &&
                Objects.equal(queryTimeout, other.queryTimeout) &&
                Objects.equal(maxIdleConnections, other.maxIdleConnections) &&
                Objects.equal(closeIdleConnectionAfter, other.closeIdleConnectionAfter) &&
                Objects.equal(checkConnectionWhenIdle, other.checkConnectionWhenIdle) &&
                Objects.equal(idleConnectionCheckInterval, other.idleConnectionCheckInterval) &&
                Objects.equal(validationQueryTimeout, other.validationQueryTimeout) &&
                Objects.equal(driverClass, other.driverClass) &&
                Objects.equal(url, other.url) &&
                Objects.equal(username, other.username) &&
                Objects.equal(password, other.password) &&
                Objects.equal(defaultTransactionIsolation, other.defaultTransactionIsolation) &&
                Objects.equal(validationQuery, other.validationQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                driverClass,
                url,
                username,
                password,
                defaultTransactionIsolation,
                autoCommit,
                checkConnectionOnBorrow,
                checkConnectionOnReturn,
                minTotalConnections,
                maxTotalConnections,
                acquireConnectionTimeout,
                queryTimeout,
                maxIdleConnections,
                closeIdleConnectionAfter,
                checkConnectionWhenIdle,
                idleConnectionCheckInterval,
                validationQuery,
                validationQueryTimeout);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("driverClass", driverClass)
                .add("url", url)
                .add("username", username)
                .add("password", password)
                .add("defaultTransactionIsolation", defaultTransactionIsolation)
                .add("autoCommit", autoCommit)
                .add("checkConnectionOnBorrow", checkConnectionOnBorrow)
                .add("checkConnectionOnReturn", checkConnectionOnReturn)
                .add("minTotalConnections", minTotalConnections)
                .add("maxTotalConnections", maxTotalConnections)
                .add("acquireConnectionTimeout", acquireConnectionTimeout)
                .add("queryTimeout", queryTimeout)
                .add("maxIdleConnections", maxIdleConnections)
                .add("closeIdleConnectionAfter", closeIdleConnectionAfter)
                .add("checkConnectionWhenIdle", checkConnectionWhenIdle)
                .add("idleConnectionCheckInterval", idleConnectionCheckInterval)
                .add("validationQuery", validationQuery)
                .add("validationQueryTimeout", validationQueryTimeout)
                .toString();
    }
}
